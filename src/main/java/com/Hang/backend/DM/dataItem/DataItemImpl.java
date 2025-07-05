package com.Hang.backend.DM.dataItem;

import com.Hang.backend.DM.DataManagerImpl;
import com.Hang.backend.DM.page.Page;
import com.Hang.backend.common.SubArray;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * dataItem中保存的数据是这样的：[ValidFlag] [DataSize] [Data]    每个page里面管理一个个dataItem
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
    private Page pg; // 页面对象  这个DataItem属于这个数据页

    // mysql中操作数据用的就是读写锁
    private Lock rLock; // 读锁
    private Lock wLock; // 写锁


    public DataItemImpl(SubArray raw, byte[] oldRaw,Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();  // （读写锁，允许多个线程并发读，但写是互斥的）   ReentrantLock是独占锁
        /*
        读锁虽然允许多个线程同时读，但是他和不加锁还是有区别的，因为读锁可以限制写操作，而不加锁不行，
        不加锁可能读到写了一半的数据，数据不一致，而在读锁中，写线程必须等待读锁释放，防止脏读，然后写的话直接用写锁进行互斥，它仍然提供了“读一致性”和“写隔离”：
         */
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.uid = uid;
        this.dm = dm;
        this.pg = pg;
    }

    public boolean isValid(){
        return raw.raw[raw.start+OF_VALID] == (byte)0;
    }

    @Override
    public SubArray data() {
        // 返回数据项中的数据部分，返回的是原始数据的引用，而不是数据拷贝
        // (用SubArray类就是为了防止原始数据的拷贝的问题，始终操作的是同一块数据)
        return new SubArray(raw.raw, raw.start+OF_DATA,raw.end);
    }

    /*
     * before() 是 修改前的准备：加锁、备份原始数据。
     * unBefore() 是 撤销修改（undo）：恢复旧数据、释放锁。
     * 它们成对使用，确保数据项要么成功更新，要么可以回滚到修改前状态。
     */

    /*
     * 在修改数据之前调用，用于锁定数据项并保存原始数据
     */
    @Override
    public void before() {
        wLock.lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw,raw.start,oldRaw,0,oldRaw.length);  // 备份原始数据
    }

    /*
     * 这就是撤销修改吧，和前面的before对应
     */
    @Override
    public void unBefore() {
        System.arraycopy(oldRaw,0,raw.raw,raw.start,oldRaw.length);
        wLock.unlock();
    }

    @Override
    public void after(long xid) {
        dm.logDataItem(xid,this);
        wLock.lock();
    }

    @Override
    public void release() {
        dm.releaseDataItem(this);
    }

    @Override
    public void lock() {
        wLock.lock();
    }

    @Override
    public void unlock() {
        wLock.unlock();
    }

    @Override
    public void rLock() {
        rLock.lock();
    }

    @Override
    public void rUnlock() {
        rLock.unlock();
    }

    @Override
    public Page page() {
        return pg;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public byte[] getOldRaw() {
        return oldRaw;
    }

    @Override
    public SubArray getRaw() {
        return raw;
    }
}
