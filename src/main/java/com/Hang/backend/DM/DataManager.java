package com.Hang.backend.DM;

import com.Hang.backend.DM.dataItem.DataItem;
import com.Hang.backend.DM.logger.Logger;
import com.Hang.backend.DM.pageCache.PageCache;
import com.Hang.backend.DM.pageCache.PageCacheImpl;
import com.Hang.backend.TM.TransactionManager;

public interface DataManager {
    /*
    read() 和 insert() 可能涉及磁盘 IO、缓存分配、日志写入、事务控制等多个出错点，必须显式抛出异常；
    而 close() 通常是一个简单的资源释放操作，不太会失败，或者即使失败也不能影响主流程，所以不强制声明异常。
     */
    DataItem read(long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    void close();

    public static DataManager create(String path, long men, TransactionManager tm) {
        PageCache pc = PageCache.create(path, men);
        Logger lg = Logger.create(path);

        DataManagerImpl dm = new DataManagerImpl(pc,lg,tm);
    }
}
