package com.Hang.backend.VM;


import com.Hang.backend.DM.DataManager;
import com.Hang.backend.TM.TransactionManager;

/**
 * 它是数据库中负责事务读写访问、隔离级别控制和版本可见性判断的中间层，负责协调 TransactionManager 和 DataManager，并隐藏版本控制逻辑。
 */
public interface VersionManager {
    byte[] read(long xid, long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    boolean delete(long xid, long uid) throws Exception;

    long begin(int level);
    void commit(long xid) throws Exception;
    void abort(long xid);

    public static VersionManager newVersionManager(TransactionManager tm, DataManager dm) {
        return new VersionManagerImpl(tm,dm);
    }
}
