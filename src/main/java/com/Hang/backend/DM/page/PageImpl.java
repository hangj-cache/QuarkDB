package com.Hang.backend.DM.page;

import com.Hang.backend.DM.pageCache.PageCache;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageImpl implements Page{
    private int pageNumber; // 页面的页号，从1开始计数
    private byte[] data; // 这个页实际包含的字节数据
    private boolean dirty; // 标志页面是否是脏页面，在缓存驱逐时，脏页面需要被写回磁盘。
    private Lock lock;

    private PageCache pc;

    public PageImpl(int pageNumber, byte[] data, PageCache pc) {
        this.pageNumber = pageNumber;
        this.data = data;
        this.pc = pc;
        lock = new ReentrantLock();
    }
    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    @Override
    public void release() {  // 将当前对象（通常是某种“资源”）释放回资源池或缓存系统中，供后续重复使用，避免重复创建。
        pc.release(this);  // this：代表当前对象，也就是当前使用的资源。
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public int getPageNumber() {
        return pageNumber;
    }

    @Override
    public byte[] getData() {
        return data;
    }
}
