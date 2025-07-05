package com.Hang.backend.IM;


import com.Hang.backend.DM.dataItem.DataItem;
import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.common.SubArray;
import com.Hang.backend.utils.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Node结构如下：
 * [LeafFlag-1][KeyNumber-2][SiblingUid-8]
 * [Son0][Key0][Son1][Key1]...[SonN][KeyN]  每个son以及key都是8个字节
 *
 * LeafFlag:1表示叶子节点，0表示普通节点
 * KeyNumber-2：2 字节，表示当前节点中有多少个 Key。
 * SiblingUid-8：8 字节，仅对叶子节点有意义，表示下一个兄弟节点(就是右兄弟节点)的 UID（链表结构，用于范围查询）。
 */


/*
B+数的节点结构，分为两种：叶子节点和非叶子节点
非叶子节点：只存 Key + 子节点 UID	起“导航”作用，用于决定去哪一个子节点继续查找
叶子节点：存 Key + 数据 UID	是真正存储数据的位置   所有数据都存在叶子节点上
（uid 全称是 Unique Identifier，表示唯一标识符。你可以理解为每个节点在磁盘上的“地址”或“编号”。）   uid:8字节

Key 是用来排序 + 路径选择的核心依据。  [ Son0 ][ Key0 ][ Son1 ][ Key1 ][ Son2 ]
这个结构意味着：
如果你要找的 key < Key0 → 去 Son0 分支
如果 Key0 <= key < Key1 → 去 Son1 分支
如果 Key1 <= key → 去 Son2 分支
 */
public class Node {
    static final int IS_LEAF_OFFSET = 0;
    static final int NO_KEYS_OFFSET = IS_LEAF_OFFSET + 1;
    static final int SIBLING_OFFSET = NO_KEYS_OFFSET + 2;
    static final int NODE_HEADER_SIZE = SIBLING_OFFSET + 8;
    /*
     * LeafFlag:1表示叶子节点，0表示普通节点
     * KeyNumber-2：2 字节，表示当前节点中有多少个 Key。
     * SiblingUid-8：8 字节，仅对叶子节点有意义，表示下一个兄弟节点的 UID（链表结构，用于范围查询）。  叶子节点使用指针连起来的！！！
     */

    /*
        平衡因子 BALANCE_NUMBER 是为了限制每个节点内最多存放多少个 key 的一个常量。也就是：控制节点大小，避免太空或太挤。
     */
    static final int BALANCE_NUMBER = 32;  //  B+树中每个节点的“平衡因子”，可以认为是一个节点最多能存储的 key 的一半。
    /*
    通常：
    平衡因子为32表示一个节点最少有 32 个键值对，最多是 2 * BALANCE_NUMBER = 64。
    一个节点最多存储 2 * BALANCE_NUMBER 个 key（即最多 64 个 key），当 key 数量达到上限，就要发生“分裂”（split）
    则子节点数最多是 65（因为子节点比 key 多 1）
    key是用来分隔区间的（有序的），每个 key 分隔出一个区间，需要两个 child 来承接 ⇒ 所以必须比 key 多一个。
    一个节点中有n个key：[ key0, key1, ..., key(n-1) ]，那么需要n+1个节点[ child0 | key0 | child1 | key1 | child2 | ... | key(n-1) | child(n) ]
     */
    static final int NODE_SIZE = NODE_HEADER_SIZE + (2 * 8) * (2 * BALANCE_NUMBER + 2);  // 一个节点占用的总字节数（为定长）
    // 对于 N 个 key，总共有 N+1 个 Son。每组 (Son, Key) 总共 65 次（最后一个 Son 没有 Key 配对）。
    // BALANCE_NUMBER * 2 + 2 = 66，比我们想象的多了一组。
    //👉 这其实是为了「预留空间」，避免溢出和拷贝时越界，增加健壮性。


    BPlusTree tree;
    DataItem dataItem;
    SubArray raw;
    long uid;

    static void setRawIsLeaf(SubArray raw, boolean isLeaf) {
        if(isLeaf){
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte)1;  // 1是叶子节点
        }else{
            raw.raw[raw.start + IS_LEAF_OFFSET] = (byte)0;  // 0是非叶子节点
        }
    }

    /*
    判断受否是叶子节点的raw
     */
    static boolean getRawIfLeaf(SubArray raw) {
        return raw.raw[raw.start+IS_LEAF_OFFSET] == (byte)1;
    }

    /*
    将 B+ 树节点中存储的键数量（noKeys）写入节点的字节数组中，即将 noKeys 写入节点元数据区域的 [KeyNumber-2] 字段。
     */
    static void setRawNoKeys(SubArray raw, int noKeys) {  // 因为KeyNumber本来就2个字节，因此将int强转为2个字节
        System.arraycopy(Parser.short2Byte((short)noKeys),0,raw.raw,raw.start+NO_KEYS_OFFSET,2);
    }

    static int getRawNoKeys(SubArray raw) {
        return (int)Parser.parseShort(Arrays.copyOfRange(raw.raw,raw.start+NO_KEYS_OFFSET,raw.start+NO_KEYS_OFFSET+2));
    }

    // 设置当前 B+ 树节点的右兄弟节点（sibling）的 UID 值。
    static void setRawSibling(SubArray raw, long sibling){
        System.arraycopy(Parser.long2Byte(sibling),0,raw.raw,raw.start+SIBLING_OFFSET,8);
    }

    static long getRawSibling(SubArray raw) {
        return Parser.parseLong(Arrays.copyOfRange(raw.raw,raw.start+SIBLING_OFFSET, raw.start+SIBLING_OFFSET+8));
    }

    /*
    将第 kth 个子节点的 UID 写入 B+ 树节点的二进制结构中对应的位置。
     */
    static void setRawKthSon(SubArray raw, long uid, int kth){
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);  // 每个 Son 是 8 字节（long），每个 Key 也是 8 字节（long）。因此每组 (Son, Key) 占用 16 字节。
        System.arraycopy(Parser.long2Byte(uid),0,raw.raw,offset,8);
    }

    static long getRawKthSon(SubArray raw, int kth){
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2);
        return Parser.parseLong(Arrays.copyOfRange(raw.raw,offset,offset+8));
    }

    static void setRawKthKey(SubArray raw, long key, int kth){
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        System.arraycopy(Parser.long2Byte(key),0,raw.raw,offset,8);
    }

    static long getRawKthKey(SubArray raw, int kth){
        int offset = raw.start + NODE_HEADER_SIZE + kth * (8 * 2) + 8;
        return Parser.parseLong(Arrays.copyOfRange(raw.raw,offset,offset+8));
    }
    /*
    从一个 B+ 树节点 from 中的第 kth 个 (Son, Key) 开始，将后续所有的 (Son, Key) 对拷贝到另一个节点 to 中的开始位置（覆盖掉其已有数据）。
    这通常用于 B+ 树分裂节点 时，把右半边 key/son 拷贝到新建节点中。
     */
    static void copyRawFromKth(SubArray from, SubArray to, int kth){
        int offset = from.start + NODE_HEADER_SIZE + kth * (8 * 2);
        System.arraycopy(from.raw,offset,to.raw,to.start+NODE_HEADER_SIZE,from.end - offset);
    }

    /*
    在 B+ 树的节点中，从第 kth 个位置开始，整体向后移动一组 (Son, Key) 数据，为插入新 (Son, Key) 腾出空间。
    那样一般最后一组son-key会丢失，一般会做如下处理
    判断是否满（比如已有 64 对）：
    如果满，先进行分裂操作（生成一个新节点、拆分一半键值对）
    然后再进行 shiftRawFromKth() 来移动、插入
    如果没满，才调用 shiftRawFromKth() 来右移数据插入
     */
    static void shiftRawFromKth(SubArray raw,int kth){
        int begin = raw.start + NODE_HEADER_SIZE + (kth+1) * (8 * 2);
        int end = raw.start + NODE_SIZE - 1;
        // 从 end 开始往前遍历到 begin，为的是避免数据覆盖
        for(int i = end; i >= begin; i--){
            raw.raw[i] = raw.raw[i - (2*8)];  // 按照字节为单位进行迁移，向后移动16字节（腾出一组 Son-Key 的空间）
        }
    }

    /*
    在 B+ 树节点的 raw 数据中，从第 kth 个位置起向后移动一个位置，为插入新的 (Son, Key) 对留出空间。
     */
    static void shiftRawKth(SubArray raw,int kth){
        int begin = raw.start + NODE_HEADER_SIZE + (kth+1) * (8 * 2);
        int end = raw.start + NODE_SIZE - 1;
        for(int i = end; i >= begin; i--){
            raw.raw[i] = raw.raw[i - (2*8)];
        }
    }

    /*
    这段代码的作用是：初始化一个非叶子根节点，这个根节点有两个子节点，并设置了分裂所需的初始关键字与子节点指针。
    用于 B+ 树在插入时根节点分裂后，新建根节点的情况。
     */
    static byte[] newRootRaw(long left, long right, long key){
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(raw, false);
        setRawNoKeys(raw, 2);
        setRawSibling(raw,0);
        setRawKthSon(raw,left,0);  // 两个RawKthSon是这样的，因为一个node里本就可以有多个son-key的嘛
        setRawKthKey(raw,key,0);
        setRawKthSon(raw,right,1);
        setRawKthKey(raw,Long.MAX_VALUE,1);

        return raw.raw;
    }

    /*
    这段代码的作用是用于初始化一个空的 B+ 树根节点（nil root），返回其原始二进制数据（byte[] 类型），可供存储或插入使用。
     */
    static byte[] newNilRootRaw(){
        SubArray raw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(raw, true);
        setRawNoKeys(raw, 0);
        setRawSibling(raw, 0);
        return raw.raw;
    }

    static Node loadNode(BPlusTree bTree, long uid) throws Exception{
        DataItem di = bTree.dm.read(uid);
        assert di != null;
        Node n = new Node();
        n.uid = uid;
        n.tree = bTree;
        n.dataItem = di;
        n.raw = di.data();
        return n;
    }

    public void release(){
        dataItem.release();
    }

    public boolean isLeaf(){
        dataItem.rLock();
        try{
            return getRawIfLeaf(raw);
        }finally{
            dataItem.rUnlock();
        }
    }

    class SearchNextRes{
        long uid;
        long siblingUid;
    }

    /*
    在当前叶子节点中查找比给定 key 更大的下一个 key（及其位置）。如果找不到，就去找兄弟节点。
     */
    public SearchNextRes searchNext(long key){
        dataItem.rLock();
        try{
            SearchNextRes res = new SearchNextRes();
            int noKeys = getRawNoKeys(raw);  // 当前节点中的 key 个数
            for(int i = 0; i < noKeys; i++){ // 这是在自个叶子节点中找
                long ik = getRawKthKey(raw,i);
                if(key < ik){  // 找到了比当前 key 更大的下一个 key
                    res.uid = getRawKthKey(raw,i);
                    res.siblingUid = 0;
                    return res;
                }
            }
            // 没找到比它大的 key ⇒ 去兄弟节点找  这是去兄弟节点找
            res.uid = 0;
            res.siblingUid = getRawSibling(raw);
            return res;
        }finally{
            dataItem.rUnlock();
        }
    }


    class LeafSearchRangeRes{
        List<Long> uids;  // 当前节点中，键值在范围内的记录 UID 列表
        long siblingUid;  // 若未查完范围，指向右兄弟节点的 UID，用于继续往右查找
    }

    /*
    在 B+ 树叶子节点 中查找某个 键值范围 ([leftKey, rightKey]) 内的记录，返回对应的 UID 列表和是否需要继续到下一个兄弟节点搜索的信息。
     */
    public LeafSearchRangeRes leafSearchRange(long leftKey, long rightKey){
        dataItem.rLock();
        try{
            int noKeys = getRawNoKeys(raw);
            int kth = 0;
            while(kth < noKeys){
                long ik = getRawKthKey(raw, kth);
                if(ik >= leftKey){
                    break;
                }
                kth++;
            }
            List<Long> uids = new ArrayList<>();
            while(kth < noKeys){
                long ik = getRawKthKey(raw, kth);
                if(ik <= rightKey){
                    uids.add(getRawKthSon(raw, kth));
                    kth++;
                }else{
                    break;
                }
            }
            long siblingUid = 0;
            if(kth == noKeys){ // 如果没有查完，就继续去查右兄弟节点
                siblingUid = getRawSibling(raw);
            }

            LeafSearchRangeRes res = new LeafSearchRangeRes();
            res.uids = uids;
            res.siblingUid = siblingUid;
            return res;
        }finally{
            dataItem.rUnlock();
        }
    }

    class InsertAndSplitRes{
        long siblingUid, newSon, newKey;
    }
    /*
    B+ 树中用于插入一个 (key, uid) 键值对到当前节点，并在必要时进行分裂的完整逻辑。
     */
    public InsertAndSplitRes insertAndSplit(long uid, long key) throws Exception{
        boolean success = false;
        Exception err = null;
        InsertAndSplitRes res = new InsertAndSplitRes();

        dataItem.before();
        try{
            success = insert(uid, key);
            if(!success){
                res.siblingUid = getRawSibling(raw);
                return res;
            }
            if(needSplit()){
                try{
                    SplitRes r = split();
                    res.newSon = r.newSon;
                    res.newKey = r.newKey;
                    return res;
                }catch (Exception e){
                    err = e;
                    throw e;
                }
            }else{
                return res;
            }
        }finally{
            if(err == null && success){
                dataItem.after(TransactionManagerImpl.SUPER_XID);
            }else{
                dataItem.unBefore();
            }
        }
    }
    /*
    B+ 树中某个节点（Node）的插入逻辑的核心部分
     */
    private boolean insert(long uid, long key){
        int noKeys = getRawNoKeys(raw);
        int kth = 0;
        while(kth < noKeys){
            long ik = getRawKthKey(raw, kth);
            if(ik <= key){
                kth++;
            }else {
                break;
            }
        }
        if(kth == noKeys && getRawSibling(raw) != 0) return false;
        if(getRawIfLeaf(raw)){
            shiftRawKth(raw, kth); // 这就是将所有数据向后移一个单位，给新插入的数据嘛
            setRawKthKey(raw,key,kth);
            setRawKthSon(raw,uid,kth);
            setRawNoKeys(raw,noKeys+1);
        }else{
            long kk = getRawKthKey(raw, kth);
            setRawKthKey(raw,key,kth);
            shiftRawKth(raw, kth+1);
            setRawKthKey(raw,kk,kth+1);
            setRawKthSon(raw,uid,kth+1);
            setRawNoKeys(raw,noKeys+1);
        }
        return true;
    }

    private boolean needSplit(){
        return BALANCE_NUMBER*2 == getRawNoKeys(raw);
    }

    class SplitRes{
        long newSon, newKey;
    }

    /*
    这段代码是 B+ 树中节点**分裂（split）**的核心逻辑，当某个节点的 key 超出容量（通常是插入后判断），就需要把当前节点一分为二，以维持树的平衡。
     */
    private SplitRes split() throws Exception{
        // 新建一个 SubArray（本质上是封装的一段字节数组），用于表示新分裂出来的节点的原始二进制数据。
        SubArray nodeRaw = new SubArray(new byte[NODE_SIZE], 0, NODE_SIZE);
        setRawIsLeaf(nodeRaw,getRawIfLeaf(raw));  // 将当前节点的“是否是叶子节点”属性复制到新节点中。说明：分裂后两个节点同属一类（都为叶子 or 都为内部节点）。
        setRawNoKeys(nodeRaw,BALANCE_NUMBER); // 设置新节点的 key 数量为 BALANCE_NUMBER。
        // BALANCE_NUMBER 是分裂后每个节点保留 key 的数量，B+ 树节点分裂一般按中间位置一分为二。
        setRawSibling(nodeRaw,getRawSibling(raw));  // 把当前节点的兄弟 UID 设置给新节点（把原来的“右兄弟”传给新节点）。
        copyRawFromKth(raw,nodeRaw,BALANCE_NUMBER); // 从当前节点的第 BALANCE_NUMBER 个 key 开始，复制一半 key/value 到新节点中。
        long son = tree.dm.insert(TransactionManagerImpl.SUPER_XID, nodeRaw.raw);
        // 新节点的原始数据插入到底层数据管理模块 dm 中，返回其 UID（类似页号或偏移量）
        setRawNoKeys(raw,BALANCE_NUMBER); // 当前节点也保留一半数据（前半部分），把 key 数量设为 BALANCE_NUMBER。
        setRawSibling(raw,son);  // 当前节点的右兄弟设置为新分裂出来的 son 节点，实现 叶子节点之间的链接更新。

        SplitRes res = new SplitRes();
        res.newSon = son;  // 新建节点的 UID。
        res.newKey = getRawKthKey(nodeRaw,0);  // 新建节点的第一个 key，用于插入到上层父节点。
        return res;
    }

    /*
    这段代码是重写了 Java 的 toString() 方法，用于调试或打印 B+ 树中某个节点的详细结构信息，
    其目的是以可读的字符串形式输出该节点的重要内容，
    包括它是否是叶子节点、拥有多少 key、兄弟节点 UID，以及每个 key 与其对应的子节点 UID（即 son）。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Is leaf:").append(getRawIfLeaf(raw)).append("\n");
        int KeyNumber = getRawNoKeys(raw);
        sb.append("KeyNumber:").append(KeyNumber).append("\n");
        sb.append("sibling:").append(getRawSibling(raw)).append("\n");
        for(int i = 0; i < KeyNumber; i++){
            sb.append("son:").append(getRawKthSon(raw,i)).append(", key:").append(getRawKthKey(raw,i)).append("\n");
        }
        return sb.toString();
    }
}


