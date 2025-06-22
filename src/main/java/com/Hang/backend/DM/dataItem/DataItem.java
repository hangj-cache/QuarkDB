package com.Hang.backend.DM.dataItem;

import com.Hang.backend.DM.DataManagerImpl;
import com.Hang.backend.DM.page.Page;
import com.Hang.backend.common.SubArray;
import com.Hang.backend.utils.Parser;
import com.Hang.backend.utils.Types;
import com.google.common.primitives.Bytes;

import java.util.Arrays;

/**
 * 存储数据的具体内容以及一些相关的元数据信息（数据的大小、有效标志等）
 * DataItem 是一个数据抽象层，它提供了一种在上层模块和底层数据存储之间进行交互的接口
 *
 * DataItem和page又不一样，这个侧重于纯数据，而不是和系统存储的交互的页数据，因此这里面没有页码，而是uid，就是记录的id，用来标识一条数据
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

    public static DataItem parseDataItem(Page pg, short offset, DataManagerImpl dm){ // dataItem是Page中的一部分，这个offset是对这个要操作的dataItem的偏移
        byte[] raw = pg.getData();
        short size = Parser.parseShort(Arrays.copyOfRange(raw,offset+DataItemImpl.OF_SIZE,offset+DataItemImpl.OF_DATA));
        // 数据项DataItem的长度
        short length = (short)(size + DataItemImpl.OF_DATA);
        long uid = Types.addressToUid(pg.getPageNumber(),offset);
        return new DataItemImpl(new SubArray(raw,offset,offset+length),new byte[length],pg,uid,dm);
    }

    public static void setDataItemRawInvalid(byte[] raw){
        raw[DataItemImpl.OF_VALID] = (byte)1;
    }

}
