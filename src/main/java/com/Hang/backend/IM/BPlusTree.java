package com.Hang.backend.IM;

import com.Hang.backend.DM.DataManager;
import com.Hang.backend.DM.dataItem.DataItem;
import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.common.SubArray;
import com.Hang.backend.utils.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/*
"Bootstrapping UID"（引导唯一标识符），即：
系统初始化时创建的根对象或入口对象的 UID（Unique Identifier）。

加上boot的是为了表示它们都与 B+ 树的根节点（或根节点的记录）有关，也就是所谓的“引导信息（bootstrap info）”。
 */
public class BPlusTree {  // BPlusTree 类只需要存储根节点的 UID，就能完整代表一棵 B+ 树
    DataManager dm;  // 底层的数据管理器，负责存储节点
    long bootUid;  // 记录“根节点 UID”的那条记录的 UID
    DataItem bootDataItem;  // 读取到的那条记录（里面保存了 rootUid）
    Lock bootLock;  // 用于更新 rootUid 的并发保护

    /*
    它的作用是 创建一棵新的 B+ 树，并返回 这棵树的 bootUid（引导记录的 UID）。

    // 步骤：
    1. 创建一个空的根节点（实际上是叶子节点）；
    2. 将其插入数据库，获取 rootUid；
    3. 创建一条仅存 rootUid 的记录（boot record）；
    4. 返回这条记录的 UID ⇒ bootUid
    // 返回值：bootUid（以后加载 B+ 树的入口）
     */
    /*
    为什么在 BPlusTree.create() 里用 SUPER_XID？
    因为在初始化 B+ 树时,1.插入根节点；2.插入 boot record（记录 rootUid）；这些都是系统级别的初始化行为
    不属于用户提交的事务；不需要做回滚、隔离级别判断；插入后立刻就可见（所以必须视为“已提交”）；因此，用 SUPER_XID 是合理的。
     */
    public static long create(DataManager dm) throws Exception {
        byte[] rawRoot = Node.newNilRootRaw();
        long rootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rawRoot);  // // 插入后返回根节点 UID
        // 将根节点的 UID 再包装成 8 字节，作为 bootDataItem 存储
        return dm.insert(TransactionManagerImpl.SUPER_XID, Parser.long2Byte(rootUid));
    }

    /*
    作用是从磁盘中 加载一棵已有的 B+ 树,由bootUid来加载
     */
    public static BPlusTree load(long bootUid,DataManager dm) throws Exception {
        DataItem bootDataItem = dm.read(bootUid);
        assert bootDataItem != null;
        BPlusTree t = new BPlusTree();
        t.bootUid = bootUid;
        t.dm = dm;
        t.bootDataItem = bootDataItem;
        t.bootLock = new ReentrantLock();
        return t;
    }

    /*
    读取当前 B+ 树根节点的 UID（唯一标识符）。
     */
    private long rootUid(){
        bootLock.lock();
        try{
            SubArray sa = bootDataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw,sa.start,sa.start+8));
            // B+数根节点前8个字节是uid，看前面create，存B+树就是将bootUid这8个字节作为bootDataItem来存的
        }finally{
            bootLock.unlock();
        }
    }


    /*
    xid 在 insert() 中的作用是什么？
    long newRootUid = dm.insert(xid, rootRaw);
    这里 dm.insert(...) 可能会生成日志、写页缓存、或者调用恢复机制。
    传入 xid 让这些组件知道：这个操作属于哪个事务。
    如果是 SUPER_XID，意味着：
    这是系统操作,不受普通事务的可见性控制,必须立即提交并对所有事务可见
     */
    /*
    当原来的根节点发生分裂（split）时，创建新的根节点，并更新 bootDataItem 中记录的根节点 UID。
    left:左孩子的 UID
    right:右孩子的 UID
    rightKey:两个孩子之间的分隔 key

    就是一个根节点满了，然后需要将根节点裂开，然后列为左半部分和右半部分，这时候没有根，就新建一个根作为这个左半部分和右半部分的根，然后去bootItem中跟新根的uid
     */
    private void updateRootUid(long left, long right, long rightKey) throws Exception{
        bootLock.lock();
        try{
            // 这一步创建的是一个非叶子节点的新根节点，它管理原来的根节点和新分裂出来的右节点。
            byte[] rootRaw = Node.newRootRaw(left, right, rightKey);
            long newRootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rootRaw);
            bootDataItem.before();
            SubArray diRaw = bootDataItem.data();
            System.arraycopy(Parser.long2Byte(newRootUid),0,diRaw.raw,diRaw.start,8);
            bootDataItem.after(TransactionManagerImpl.SUPER_XID);
        }finally{
            bootLock.unlock();
        }
    }

    /*
    searchLeaf 的作用是 在一棵 B+ 树结构中递归查找包含给定键 key 的叶子节点的 UID（唯一标识符）。
     */
    private long searchLeaf(long nodeUid, long key) throws Exception{
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        if(isLeaf){
            return nodeUid;
        }else{
            long next = searchNext(nodeUid, key);
            return searchLeaf(next,key);
        }
    }

    /*
    searchNext 方法是为 B+ 树的中间节点（非叶子）nodeUid中查找 key 所属的子节点 设计的逻辑。
     */
    private long searchNext(long nodeUid, long key) throws Exception{
        while(true){  // 无限循环，直到找到合适的子节点为止。说明：某些情况下可能需要跳到兄弟节点继续查找。
            Node node = Node.loadNode(this, nodeUid);  // 加载当前 UID 对应的节点。
            Node.SearchNextRes res = node.searchNext(key);  // 在当前节点中查找合适的子节点  searchNext会遍历当前节点的所有key
            node.release();  // 释放当前节点资源（比如缓存或锁）。
            if(res.uid != 0) return res.uid;  // 如果找到了合适的子节点 UID，就返回它，结束循环。
            nodeUid = res.siblingUid;  // 否则，将当前 UID 设置为右兄弟节点 UID，继续循环查找。
        }
    }

    public List<Long> search(long key) throws Exception{
        return searchRange(key,key);
    }

    /*
     在 B+ 树中查找一个键区间 [leftKey, rightKey] 所覆盖的所有叶子节点中包含的记录 UID 的过程。
    它是对 B+ 树结构中 范围查询（range search） 的一个经典实现。
     */
    public List<Long> searchRange(long leftKey, long rightKey) throws Exception{
        long rootUid = rootUid();  // 获取当前 B+ 树的根节点 UID，作为查找起点。
        long leafUid = searchLeaf(rootUid, leftKey);  // 调用 searchLeaf 方法，定位包含 leftKey 的第一个叶子节点。
        List<Long> uids = new ArrayList<>();
        while(true){ // 从找到的第一个叶子节点开始，依次遍历叶子节点链表，直到超出 rightKey。
            Node leaf = Node.loadNode(this, leafUid);
            Node.LeafSearchRangeRes res = leaf.leafSearchRange(leftKey, rightKey);
            leaf.release();
            uids.addAll(res.uids);
            if(res.siblingUid == 0){
                break;
            }else {
                leafUid = res.siblingUid;
            }
        }
        return uids;
    }

    /*
    insert(long key, long uid) 方法是 B+ 树插入逻辑的顶层入口 —— 在整棵 B+ 树中插入一条 (key, uid) 数据项。
     */
    public void insert(long key, long uid) throws Exception{
        long rootUid = rootUid();
        InsertRes res = insert(rootUid, key, uid);
        assert res != null;
        if(res.newNode != 0){
            updateRootUid(rootUid, res.newNode, res.newKey);
        }
    }

    class InsertRes{
        long newNode, newKey;
    }

    /*
    递归地将 (key, uid) 插入树中对应的叶子节点，并在需要时处理节点的 分裂与向上冒泡。
     */
    private InsertRes insert(long nodeUid, long uid, long key) throws Exception{
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();

        InsertRes res = null;
        if(isLeaf){
            res = insertAndSplit(nodeUid, uid, key);
        }else{
            long next = searchNext(nodeUid, key);
            InsertRes ir = insert(next, uid, key);
            if(ir.newNode != 0){
                res = insertAndSplit(nodeUid, ir.newNode, ir.newKey);
            }else{
                res = new InsertRes();
            }
        }
        return res;
    }

    /*
    insertAndSplit(...) 是 B+ 树插入流程中真正执行数据插入并判断是否需要分裂节点的关键步骤之一。

    nodeUid: 当前要插入的节点 UID。
    uid: 数据项的 UID（指向实际数据内容的位置，如磁盘地址）。
    key: 要插入的 key（排序依据）。
     */
    private InsertRes insertAndSplit(long nodeUid, long uid, long key) throws Exception{
        while(true){
            Node node = Node.loadNode(this, nodeUid);  // 加载当前节点对象。
            Node.InsertAndSplitRes iasr = node.insertAndSplit(uid, key);  // 调用节点自身的 insertAndSplit 方法执行插入。
            // 单个一个节点的插入就是找到本节点中第一个比传入key大的key
            // 返回结果 iasr 是一个封装插入结果的对象
            node.release();
            if(iasr.siblingUid != 0){  // 如果发生了分裂（siblingUid != 0）
                InsertRes res = new InsertRes(); // 创建一个新的 InsertRes，设置新节点 UID 和中间键。
                res.newNode = iasr.newSon;
                res.newKey = iasr.newKey;
                return res;  // 返回给上层递归，用于进一步插入父节点。
            }
        }
    }

    public void close(){
        bootDataItem.release();
    }

}
