package com.Hang.backend.DM.page;


import com.Hang.backend.DM.pageCache.PageCache;
import com.Hang.backend.utils.Parser;

/**
 * PageX管理普通页
 * 普通页结构
 * [FreeSpaceOffset] [Data]
 * FreeSpaceOffset: 2字节 空闲位置开始偏移
 */
public class PageX {
    // 一个普通页面以一个 2 字节无符号数起始，表示这一页的空闲位置的偏移。剩下的部分都是实际存储的数据。
    private static final short OF_FREE = 0;
    private static final short OF_DATA = 2;

    public static byte[] initRaw(){
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setFSO(raw,OF_DATA);
        return raw;
    }
    // 所以对普通页的管理，基本都是围绕着对 FSO（Free Space Offset）进行的
    // FSO就是空闲空间偏移，FSO 用于记录已用空间和剩余空间的分界点----普通页前两个字节记录的数据就是这个FSO的位置
    private static void setFSO(byte[] raw, short ofData){
        System.arraycopy(Parser.short2Byte(ofData),0,raw,OF_FREE,OF_DATA);
    }

    // 获取pg的FSO
    public static short getFSO(Page pg){
        return getFSO(pg.getData());
    }

    private static short getFSO(byte[] raw) {
        return Parser.parseShort(raw);  // short本身就只占两个字节，所以这里只会解析前面两个字节
    }

    // 将raw插入pg中，返回插入位置
    public static short insert(Page pg, byte[] raw){  // raw只是实际数据而已
        pg.setDirty(true);
        short offset = getFSO(pg.getData());
        System.arraycopy(raw,0,pg.getData(),offset,raw.length);
        setFSO(pg.getData(), (short) (offset+raw.length));
        return offset;
    }

    // 获取页面的空闲空间大小
    public static int getFreeSpace(Page pg){
        return PageCache.PAGE_SIZE - (int)getFSO(pg.getData());
    }

    /*
    recoverInsert() 和 recoverUpdate() 用于在数据库崩溃后重新打开时，恢复例程直接插入数据以及修改数据使用。
     */
    // 将raw插入pg的offset位置，并将pg的offset设置为较大的offset
    public static void recoverInsert(Page pg, byte[] raw, short offset){
        pg.setDirty(true);
        System.arraycopy(raw,0,pg.getData(),offset,raw.length);
        short rawFSO = getFSO(pg.getData());
        if(rawFSO < offset + raw.length){
            setFSO(pg.getData(), (short) (offset+raw.length));
        }
    }

    // 将raw插入pg中的offset位置，不更新update===就是不更新FSO
    public static void recoverUpdate(Page pg, byte[] raw, short offset){
        pg.setDirty(true);
        System.arraycopy(raw,0,pg.getData(),offset,raw.length);
    }
}
