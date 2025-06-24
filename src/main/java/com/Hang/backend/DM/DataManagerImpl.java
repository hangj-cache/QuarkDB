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
import com.Hang.common.Error;

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
    }

    @Override
    public void close() {

    }

    @Override
    protected DataItem getForCache(long key) throws Exception {
        return null;
    }

    @Override
    protected void releaseForCache(DataItem obj) {

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
