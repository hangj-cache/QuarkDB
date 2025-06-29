package com.Hang.backend.VM;


import com.Hang.backend.DM.pageCache.PageCache;
import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.common.AbstractCache;

import java.util.HashMap;
import java.util.Map;

/**
 * Transaction 类表示一个具体的事务实例，封装了它的编号、隔离级别、读快照、错误信息以及是否被系统自动终止的标志，是事务调度和并发控制的核心对象之一。
 *
 * 代表一组原子操作, 就是事务嘛
 * Transaction 是数据库中对一组原子性操作的封装对象，它确保这组操作要么全部成功，要么全部失败，同时还遵守一定的隔离级别以保障并发安全。
 *
 * 快照的含义：包含了当时所有活跃（未提交）的事务的列表，这其实就是一份“当前有哪些事务还没结束”的清单。
 * 这个清单用来判断哪些数据版本是“不可见”的，避免读到还未提交的脏数据。
 */
public class Transaction {
    public long xid;  // 事务id
    public int level;  // 事务隔离级别
    /*
    0：表示 Read Committed（读已提交）
    1：表示 Repeatable Read（可重复读）
     */
    public Map<Long, Boolean> snapshot;  // 快照
    /*
    快照视图：记录了事务启动时系统中活跃的其他事务ID。
    这是实现 可重复读（RR） 的关键。
    如果隔离级别为 0，不使用快照，设为 null。
    在 level != 0 时（如可重复读），快照在事务启动时就捕获了“当前正在执行的所有事务”的快照，用于后续判断哪些版本的数据可见。

    假设你现在开始事务T100，当前系统中正在执行 T90 和 T95：
    那么 snapshot = {90: true, 95: true}（这表示：在你当前事务（比如 T100）启动的那一刻，系统中仍在运行中、尚未提交的事务有：事务 90、事务 95）
    之后如果某条数据是由 T95 创建的，那你事务T100 读不到（因为它在 snapshot 中，正在运行还未提交，不能看）。
    “在我 T100 开启时，T90 和 T95 正在运行，还没有提交，我不信任它们的数据，也不看它们产生的内容。”
    但如果是由 T80 创建的，你就能看到（T80在你事务开始前就提交了，不在snapshot里面）。
     */
    public Exception err;
    /*
    事务当前遇到的异常。
    如果事务过程中发生了并发冲突（如死锁、版本跳跃等），这个字段会被赋值，表示事务出现了错误，后续操作应中止。
     */
    public boolean autoAborted;
    /*
    标记该事务是否被系统自动中止（如死锁、版本跳跃）。
    如果事务发生异常，系统会自动调用 abort() 方法，并设置该值为 true。
     */

    public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active) {
        // active 代表当前系统中所有活跃事务的映射表（Map<Long, Transaction>）
        Transaction t = new Transaction();
        t.xid = xid;
        t.level = level;
        if(level != 0){  // 就是可重复读，因此需要快照
            t.snapshot = new HashMap<>();
            for(Long key : active.keySet()){
                t.snapshot.put(key, true);
            }
        }
        return t;
    }

    public boolean isInactive(long xid){
        if(xid == TransactionManagerImpl.SUPER_XID){
            return false;
        }
        return snapshot.containsKey(xid);
    }
}
