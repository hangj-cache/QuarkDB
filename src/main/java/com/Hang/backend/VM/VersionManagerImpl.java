package com.Hang.backend.VM;


import com.Hang.backend.DM.DataManager;
import com.Hang.backend.TM.TransactionManager;
import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.common.AbstractCache;
import com.Hang.backend.utils.Panic;
import com.Hang.common.Error;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 1. 管理事务的版本可见性
 * 根据事务的隔离级别和版本快照，判断某条数据版本对当前事务是否可见。
 *
 * 通过 read() 方法，返回对当前事务可见的数据版本。
 *
 * 2. 封装数据的版本操作
 * 插入新数据时，封装成带版本信息的 Entry（含 XMIN，XMAX），调用底层 DataManager 实际写入。
 *
 * 删除数据时，标记版本的 XMAX 为当前事务ID，实现逻辑删除。
 *
 * 3. 事务生命周期管理
 * 开启事务 begin()，创建事务快照与状态。
 *
 * 提交事务 commit()，清理相关状态并调用事务管理器提交。
 *
 * 回滚事务 abort()，撤销事务，清理锁和状态。
 *
 * 4. 死锁检测与并发冲突控制
 * 通过内部的 LockTable 管理事务锁资源，检测并避免死锁。
 *
 * 在删除操作时检测版本冲突，自动回滚冲突事务。
 *
 * 5. 缓存管理
 * 继承自 AbstractCache<Entry>，缓存Entry，减少IO开销，提高效率。
 */
public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager {

    TransactionManager tm;
    DataManager dm;
    Map<Long, Transaction> activeTransactions;
    Lock lock;
    LockTable lt;

    public VersionManagerImpl(TransactionManager tm, DataManager dm) {
        super(0);
        this.tm = tm;
        this.dm = dm;
        this.activeTransactions = new HashMap<Long, Transaction>();
        activeTransactions.put(TransactionManagerImpl.SUPER_XID,Transaction.newTransaction(TransactionManagerImpl.SUPER_XID,0,null));
        this.lock = new ReentrantLock();
        this.lt = new LockTable();
    }

    /*
    用于在 多版本并发控制（MVCC） 下读取数据
     */
    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransactions.get(xid);
        lock.unlock();

        if(t.err != null){
            throw t.err;
        }

        Entry entry = null;
        try{
            entry = super.get(uid);
        } catch(Exception e){
            if(e == Error.NullEntryException){
                return null;
            }else{
                throw e;
            }
        }

        try{
            if(Visibility.isVisible(tm,t,entry)){
                return entry.data();
            }else{
                return null;
            }
        }finally{
            entry.release();
        }
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        lock.lock();  // 使用全局锁 lock 来保证对 activeTransactions 的并发访问安全。
        Transaction t = activeTransactions.get(xid);  // 获取当前事务对象
        lock.unlock();
        if(t.err != null){  // 如果事务曾经出现过错误（如死锁中止、并发冲突），err 不为空，直接抛出异常，不允许继续插入数据。
            throw t.err;
        }

        byte[] raw = Entry.wrapEntryRaw(xid, data);  // 包装成Entry格式
        return dm.insert(xid,raw);
    }

    @Override
    public boolean delete(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransactions.get(xid);
        lock.unlock();
        if(t.err != null){
            throw t.err;
        }
        Entry entry = null;
        try{
            entry = super.get(uid);
        }catch(Exception e){
            if(e == Error.NullEntryException){
                return false;
            }else{
                throw e;
            }
        }
        try{
            if(!Visibility.isVisible(tm,t,entry)){
                return false;
            }
            Lock l = null;
            try{
                l = lt.add(xid, uid);
            }catch(Exception e){
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid,true);
                t.autoAborted = true;
                throw t.err;
            }
            if(l != null){
                l.lock();
                l.unlock();
            }

            if(entry.getXmax() == xid){
                return false;
            }

            if(Visibility.isVersionSkip(tm,t,entry)){
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid,true);
                t.autoAborted = true;
                throw t.err;
            }
            entry.setXmax(xid);
            return true;
        }finally{
            entry.release();
        }
    }

    @Override
    public long begin(int level) {
        lock.lock();
        try{
            long xid = tm.begin();
            Transaction t = Transaction.newTransaction(xid,level,activeTransactions);
            activeTransactions.put(xid,t);
            return xid;
        }finally{
            lock.unlock();
        }
    }

    @Override
    public void commit(long xid) throws Exception {
        lock.lock();
        Transaction t = activeTransactions.get(xid);
        lock.unlock();
        try{
            if(t.err != null){
                throw t.err;
            }
        } catch (NullPointerException n) {
            System.out.println(xid);
            System.out.println(activeTransactions.keySet());
            Panic.panic(n);
        }

        lock.lock();
        activeTransactions.remove(xid);
        lock.unlock();
        lt.remove(xid);
        tm.commit(xid);
    }

    @Override
    public void abort(long xid) {
        internAbort(xid,false);
    }

    /*
    执行一次内部(intern)事务中止（abort）操作，并且根据是否是“自动中止”进行不同处理。

    自动中止指的是系统自动终止
    常见自动中止的情况：
    死锁检测失败：
    事务在尝试获取资源时，系统检测到死锁（通过依赖图 DFS 检测）
    解决方式是中止其中一个事务（通常是当前这个）
    表现为抛出 DeadlockException，然后调用 internAbort(xid, true)
    并发冲突（版本跳跃）：
    在 delete() 操作中，如果出现版本跳跃，比如其他事务已经修改该记录
    当前事务无法安全继续操作，系统为了保证隔离性，自动中止该事务
    调用 internAbort(xid, true)
     */
    private void internAbort(long xid, boolean autoAborted){  // autoAborted为true的话，就一般是出现死锁了
        lock.lock();
        Transaction t = activeTransactions.get(xid);  // 加锁保护线程安全，从事务表中获取该事务 t
        if(!autoAborted){
            activeTransactions.remove(xid); // 不是自动终止，就手动终止
        }
        lock.unlock();

        if(t.autoAborted) return;  // 如果这个事务之前已经被“自动中止”过，就不要重复执行下面的逻辑
        // 防止“重复中止”同一个事务，比如事务已死锁被自动中止过一次
        lt.remove(xid);  // 从 锁表 LockTable 中清除这个事务的锁信息
        tm.abort(xid);  // 最后通知 事务管理器 TM 执行真正的“事务中止”操作
    }

    public void releaseEntry(Entry entry){
        super.release(entry.getUid());
    }

    @Override
    protected Entry getForCache(long uid) throws Exception {  // 就是从缓存中获取资源，如果缓存中没有才从磁盘中读取，这和前面的类似，只是这里给他组装成Entry了
        Entry entry = Entry.loadEntry(this, uid);
        if(entry == null){
            throw Error.NullEntryException;
        }
        return entry;
    }

    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }
}
