package com.Hang.backend.DM.page;

/**
 * 页面的接口指的是定义页面的一些基本属性（方法），包含加锁，脏数据属性，页码以及具体数据
 */
public interface Page {
    void lock();
    void unlock();
    void release();
    void setDirty(boolean dirty);
    boolean isDirty();
    int getPageNumber();
    byte[] getData();
}
