package com.Hang.backend.DM.dataItem;

import com.Hang.backend.DM.page.Page;
import com.Hang.backend.common.SubArray;
import com.Hang.backend.utils.Parser;
import com.google.common.primitives.Bytes;

/**
 * 存储数据的具体内容以及一些相关的元数据信息（数据的大小、有效标志等）
 * DataItem 是一个数据抽象层，它提供了一种在上层模块和底层数据存储之间进行交互的接口
 */

public interface DataItem {
    SubArray data();  // 这是要给抽象方法，没有方法题，方法名就是data,()这是参数列表

    void before();
    void unBefore();
    void after(long xid);
    void release();

    void lock();
    void unlock();
    void rLock();
    void rUnlock();

    Page page();
    long getUid();
    byte[] getOldRaw();
    SubArray getRaw();

    public static byte[] wrapDataItemRaw(byte[] raw){
        byte[] valid = new byte[1];
        byte[] size = Parser.short2Byte((short) raw.length);
        return Bytes.concat(valid,size,raw);
    }

}
