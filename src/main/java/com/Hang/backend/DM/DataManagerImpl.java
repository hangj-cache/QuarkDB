package com.Hang.backend.DM;

import com.Hang.backend.DM.dataItem.DataItem;
import com.Hang.backend.DM.dataItem.DataItemImpl;
import com.Hang.backend.DM.logger.Logger;
import com.Hang.backend.DM.page.Page;
import com.Hang.backend.DM.page.PageOne;
import com.Hang.backend.DM.page.PageX;
import com.Hang.backend.DM.pageCache.PageCache;
import com.Hang.backend.DM.pageIndex.PageIndex;
import com.Hang.backend.DM.pageIndex.PageInfo;
import com.Hang.backend.TM.TransactionManager;
import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.common.AbstractCache;
import com.Hang.backend.utils.Panic;
import com.Hang.backend.utils.Types;
import com.Hang.common.Error;

/**
 * DataManagerImpl 是数据库的 数据读写管理模块，负责：     DataManagerImpl 直接管理的是 DataItem，但它间接管理的是 Page 数据。
 *
 * 管理数据的插入与读取（insert/read）
 *
 * 与页缓存 PageCache 协作管理数据页
 *
 * 使用 WAL 日志保证事务一致性
 *
 * 利用 PageIndex 快速选择空闲页
 *
 * 管理页的缓存与释放
 */
public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {

    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);  // 这是调用了父类的AbstractCache(int maxResource)，存储maxResource
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        pIndex = new PageIndex();
    }


    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl) super.get(uid);
        if(!di.isValid()){
            di.release();
            return null;
        }
        return di;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if(raw.length > PageX.MAX_FREE_SPACE){
            throw Error.DataTooLargeException;
        }

        PageInfo pi = null;
        for (int i = 0; i < 5; i++) {
            pi = pIndex.select(raw.length);
            if(pi != null){
                break;
            }else{
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if(pi == null){
            throw Error.DatabaseBusyException;
        }

        Page pg = null;
        int freeSpace = 0;
        try{
            pg = pc.getPage(pi.pgno);
            byte[] log = Recover.insertLog(xid,pg,raw);
            logger.log(log);

            short offset = PageX.insert(pg, raw);
            pg.release();  // 这个release其实就是将数据的更改刷写到磁盘上，保证数据的一致性
            return Types.addressToUid(pi.pgno,offset);
        }finally{
            if(pg != null){
                pIndex.add(pi.pgno,PageX.getFreeSpace(pg));
            }else{
                pIndex.add(pi.pgno,freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    // 为xid生成日志
    public void logDataItem(long xid, DataItem di){
        byte[] log = Recover.updateLog(xid,di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di){
        super.release(di.getUid());
    }

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;  // >>> 是 无符号右移(两个箭头的是有符号的)，把 UID 向右移动 32 位，原高位变为低位  这个原本应该是uid = uid >>> 32;
        int pgno = (int)(uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg,offset,this);
    }

    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    // 创建文件时初始化PageOne
    void initPageOne(){
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try{
            pageOne = pc.getPage(pgno);
        }catch (Exception e){
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 在打开已有文件时读取PageOne，并验证正确性
    boolean loadCheckPageOne(){
        try{
            Page pageOne = pc.getPage(1);
        }catch (Exception e){
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 初始化pageIndex
    /*
    在数据库启动时，扫描现有的所有页，并将它们初始化加入“空闲空间索引”（pIndex）中，表示这些页当前都有最大空闲空间可用。
     */
    void fillPageIndex(){
        int pageNumber = pc.getPageNumber();
        for(int i = 0; i <= pageNumber; i++){
            Page pg = null;
            try{
                pg = pc.getPage(i);
            }catch (Exception e){
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.MAX_FREE_SPACE);
            pg.release();
        }
    }
}
