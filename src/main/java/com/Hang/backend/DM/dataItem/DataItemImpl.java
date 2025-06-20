package com.Hang.backend.DM.dataItem;

import com.Hang.backend.DM.DataManagerImpl;
import com.Hang.backend.DM.page.Page;
import com.Hang.backend.common.SubArray;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * dataItem中保存的数据是这样的：[ValidFlag] [DataSize] [Data]
 * validFlag占一个字节，标志这个DataItem是否有效
 * DataSize占两个字节，标志后面Data的长度
 * 其余都是Data
 */
public class DataItemImpl implements DataItem {

    static final int OF_VALID = 0; // 有效标志字段偏移量
    static final int OF_SIZE = 1; // 长度字段偏移量
    static final int OF_DATA = 3; // 数据字段的偏移量

    private SubArray raw; // 原始数据
    private byte[] oldRaw; // 旧的原始数据
    private DataManagerImpl dm; // 数据管理器
    private long uid; // 唯一标识符
    private Page pg; // 页面对象

    // mysql中操作数据用的就是读写锁
    private Lock rLock; // 读锁
    private Lock wLock; // 写锁


    public DataItemImpl(SubArray raw, byte[] oldRaw,Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.uid = uid;
        this.dm = dm;
        this.pg = pg;
    }


    @Override
    public SubArray data() {
        // 返回数据项中的数据部分，返回的是原始数据的引用，而不是数据拷贝
        // (用SubArray类就是为了防止原始数据的拷贝的问题，始终操作的是同一块数据)
        return new SubArray(raw.raw, raw.start+OF_DATA,raw.end);
    }

    /*
     * 在修改数据之前调用，用于锁定数据项并保存原始数据
     */
    @Override
    public void before() {

    }

    @Override
    public void unBefore() {

    }

    @Override
    public void after(long xid) {

    }

    @Override
    public void release() {

    }

    @Override
    public void lock() {

    }

    @Override
    public void unlock() {

    }

    @Override
    public void rLock() {

    }

    @Override
    public void rUnlock() {

    }

    @Override
    public Page page() {
        return null;
    }

    @Override
    public long getUid() {
        return 0;
    }

    @Override
    public byte[] getOldRaw() {
        return new byte[0];
    }

    @Override
    public SubArray getRaw() {
        return null;
    }
}
