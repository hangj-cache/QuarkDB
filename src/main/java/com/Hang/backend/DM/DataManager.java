package com.Hang.backend.DM;

import com.Hang.backend.DM.dataItem.DataItem;
import com.Hang.backend.DM.logger.Logger;
import com.Hang.backend.DM.page.PageOne;
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
        if(!dm.loadCheckPageOne()){
            Recover.recover(tm,lg,pc);
        }
        dm.fillPageIndex();  // 启动的时候对每一页进行最大空闲空间的初始化操作一开始都是最大空间8kb
        PageOne.setVcOpen(dm.pageOne);  // 就是初始化好版本控制的一个字段，一个随机数  RandomUtil.randomBytes(LEN_VC)
        dm.pc.flushPage(dm.pageOne);  // 就是对这一页进行一个刷盘
        return dm;
    }

    /*
    打开一个已有的数据库文件，或初始化一个新的数据管理器（DataManager）
    并确保：
    恢复机制正常
    索引加载完毕
    第一页的版本控制数据（PageOne）有效
     */
    public static DataManager open(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.open(path, mem);
        Logger lg = Logger.open(path);
        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        if(!dm.loadCheckPageOne()) {
            Recover.recover(tm, lg, pc);
        }
        dm.fillPageIndex();
        PageOne.setVcOpen(dm.pageOne);
        dm.pc.flushPage(dm.pageOne);

        return dm;
    }
}
