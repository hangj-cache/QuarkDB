package com.Hang.backend.VM;

import com.Hang.backend.DM.dataItem.DataItem;
import com.Hang.backend.common.SubArray;
import com.Hang.backend.utils.Parser;
import com.google.common.primitives.Bytes;

import java.util.Arrays;

/**
 * 对底层 DataItem（数据页中真实数据）进行包装，加入事务控制字段（XMIN、XMAX）以支持 MVCC 多版本并发控制。
 *
 * Entry(记录)
 * DM 层向上层提供了数据项（Data Item）的概念，VM 通过管理所有的数据项，向上层提供了记录（Entry）的概念。
 * 上层模块通过 VM 操作数据的最小单位，就是记录。VM 则在其内部，为每个记录，维护了多个版本（Version）。
 * 每当上层模块对某个记录进行修改时，VM 就会为这个记录创建一个新的版本。
 *
 * vm向上层抽象出entry
 * entry结构：
 * [XMIN] [XMAX] [data]   这个就是DataItem中的data部分  这两个新的字段是用来实现版本控制的
 *
 *     xmin（8字节 long 类型）代表插入该记录的事务 ID。
 *     用于判断：这个记录是谁插入的？它是否已经被提交？
 *
 *     xmax 表示删除该记录的事务 ID。
 *     若为 0 表示尚未被删除。
 *
 * 每个entry是对每个数据项DataItem来说的，每个DataItem就对应一条记录，就是一个insert或者update这样子
 */

public class Entry {
    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN + 8;
    private static final int OF_DATA = OF_XMAX + 8;

    /*
    xmin（8字节 long 类型）代表插入该记录的事务 ID。
    用于判断：这个记录是谁插入的？它是否已经被提交？

    xmax 表示删除该记录的事务 ID。
    若为 0 表示尚未被删除。
     */

    private long uid;
    private DataItem dataItem;
    private VersionManager vm;
    /*
    Entry 中保存 VersionManager 实例，不是因为每个 Entry 需要拥有“不同”的 vm，而是因为：
    Entry 是一个数据与上下文的组合体
    它需要知道是谁（哪个 vm）管理了它，从而回调做释放、加载、校验等操作

    即便整个系统只有一个 VersionManager 实例，Entry 里依然持有 vm，是为了：

    ✅ 解耦、职责明确（面向对象设计原则）
    ✅ 保证 Entry 可以自我管理 —— 比如释放、版本判断时，不依赖外部传参
    ✅ 方便测试、调试、替换 VM 实现（可扩展性）
     */

    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        if(dataItem == null){
            return null;
        }

        Entry entry = new Entry();
        entry.dataItem = dataItem;
        entry.uid = uid;
        entry.vm = vm;
        return entry;
    }

    public static Entry loadEntry(VersionManager vm, long uid) throws Exception{
        DataItem di = ((VersionManagerImpl)vm).dm.read(uid);
        return newEntry(vm,di,uid);
    }

    /*
    这个方法包装成entryraw，格式：[XMIN] [XMAX] [data]
     */
    public static byte[] wrapEntryRaw(long xid, byte[] data){
        byte[] xmin = Parser.long2Byte(xid);
        byte[] xmax = new byte[8];  // 8为全零，表示没被删除
        return Bytes.concat(xmin,xmax,data);
    }

    /*
    release就是释放一个资源，同时刷盘防止数据不一致问题
     */
    public void release(){
        ((VersionManagerImpl)vm).releaseEntry(this);
    }

    public void remove(){
        dataItem.release();
    }

    public byte[] data(){
        dataItem.rLock();
        try{
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw,sa.start+OF_DATA,data,0,data.length);
            return data;
        }finally{
            dataItem.rUnlock();
        }
    }

    /*
    [ XMIN: 8 bytes ][ XMAX: 8 bytes ][ DATA: n bytes ]  Entry的结构，这是封装在DataItem中的Data的内部的

    DataItem 是底层的「容器」，结构如下：[ ValidFlag: 1 byte ][ DataSize: 2 bytes ][ Data: n bytes ]
    在 VM（版本管理器）中，插入的 “业务数据” 会被包装成：[ XMIN: 8 bytes ][ XMAX: 8 bytes ][ 用户数据 DATA: n bytes ]
    VM 的 Entry 就是被完整包裹进 DataItem 的 Data 部分！
     */
    public long getXmin(){
        dataItem.rLock();
        try{
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw,sa.start+OF_XMIN,sa.start+OF_XMAX));
        }finally{
            dataItem.rUnlock();
        }
    }

    public long getXmax(){
        dataItem.rLock();
        try{
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw,sa.start+OF_XMAX,sa.start+OF_DATA));
        }finally{
            dataItem.rUnlock();
        }
    }

    /*
    将当前事务 ID（xid）写入到该 Entry 的 XMAX 字段中，表示这条记录被该事务删除了。
     */
    public void setXmax(long xid){
        dataItem.before();
        try{
            SubArray sa = dataItem.data();
            System.arraycopy(Parser.long2Byte(xid),0,sa.raw,sa.start+OF_XMAX,8);
        }finally{
            dataItem.rUnlock();
        }
    }

    public Long getUid(){
        return uid;
    }
}
