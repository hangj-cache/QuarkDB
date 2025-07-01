package com.Hang.backend.VM;


import com.Hang.common.Error;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LockTable 是一个锁调度器 + 死锁检测器，用于追踪哪些事务锁住了哪些数据（UID），哪些事务正在等待哪些资源，同时维护一个依赖图来检测死锁。
 *
 * 维护了一个依赖等待图，以进行死锁检测
 *
 * XID：事务ID
 * UID：资源 ID（通常是数据库中的某条记录）
 */
public class LockTable {
    private Map<Long, List<Long>> x2u;  // 记录某个事务（XID）已经成功获得锁的资源（UID）列表。
    /*
    如果事务 T10 成功获得了 UID 5 和 UID 8 的锁：
     x2u = {10L: [5L, 8L]}
     */
    private Map<Long, Long> u2x;  // 表示某个资源（UID）当前被哪个事务（XID）持有（加锁了）。
    /*
    如果 UID 5 被事务 T10 加锁了：
    u2x = {5L: 10L}
     */
    private Map<Long, List<Long>> wait;  // 表示正在等待某个资源 UID 的事务列表。    wait：某一给资源的等待列表
    /*
    如果事务 T11 和 T12 都在等 UID 5 被释放：
    wait = {5L: [11L, 12L]}
     */
    private Map<Long, Lock> waitLock;  // 为每个等待中的事务（XID）提供一个 Java 的 Lock 对象，用来阻塞线程，直到资源可用。
    /*
    如果事务 T11 正在等 UID 5，我们会为它创建一个 Lock（ReentrantLock），加上锁之后阻塞，等 UID 5 可用了再释放。
    waitLock = {11L: LockObject}

    “等待锁”指的是：事务暂时还不能获得某个资源（如某条记录）的访问权，它处于等待其他事务释放锁的状态。，就是加上锁这个事务就阻塞，等到资源可用的时候这个锁释放
     */
    private Map<Long ,Long> waitU;  // 记录每个正在等待的事务（XID）当前正在等待的资源 UID。   事务a等待资源b
    /*
    事务 T11 当前正在等 UID 5：
    waitU = {11L: 5L}
     */
    private Lock lock;

    public LockTable(){
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
    }

    // 不需要等待则返回null，否则返回锁对象
    // 会造成死锁则排除异常
    /*
    目的：
    当一个事务尝试访问某个数据（如一条记录）时，它需要先获取对应的锁，这个方法就是处理：
    能不能立刻获得锁？
    是否要等待？
    是否会造成死锁？
     */
    public Lock add(long xid, long uid) throws Exception{  // 目的就是想让事务xid后的uid资源
        lock.lock();
        try {
            if(isInList(x2u,xid,uid)){  // 如果uid已经被xid拿到了
                return null;
            }
            if(!u2x.containsKey(uid)){
                u2x.put(uid,xid);
                putIntoList(x2u,xid,uid);
                return null;
            }
            waitU.put(xid,uid);
            putIntoList(wait,uid,xid);
            if(hasDeadLock()){
                waitU.remove(xid); // 如果有死锁，就不让他等待了
                removeFromList(wait,uid,xid);
                throw Error.DeadlockException;
            }
            // 如果没有死锁进行后续的操作
            Lock l = new ReentrantLock();
            l.lock();
            waitLock.put(xid,l); // 然后需要阻塞
            return l;
        }finally{
            lock.unlock();
        }

    }

    /*
    在事务 xid 结束时，释放它占有的所有资源（锁）和清理它在等待队列中的状态。
     */
    public void remove(long xid) {
        lock.lock();
        try{
            List<Long> l = x2u.get(xid);
            if(l != null){
                while(l.size() > 0){
                    Long uid = l.remove(0);
                    selectNewXID(uid);
                }
            }
            waitU.remove(xid);
            x2u.remove(xid);
            waitLock.remove(xid);
        }finally {
            lock.unlock();
        }
    }

    // 从等待队列中选择一个xid来占用uid
    /*
    当某个资源 UID 的锁被释放后，从等待队列中选择一个事务（XID）来继续持有这个 UID 的锁。
    它是 LockTable 死锁检测/锁释放机制的关键一环。

    参数： uid 是刚刚被释放的资源 ID（例如一个数据项）
    目标： 从 wait 队列中选出一个事务来接管这个资源
     */
    private void selectNewXID(long uid){
        u2x.remove(uid);  // 移除旧的持有者（这个资源刚刚被释放）
        List<Long> l = wait.get(uid);  // 这是要开始获得新的持有者
        if(l == null) return;  // 获取等待这个资源的事务队列（FIFO 队列），没有人等待则直接返回。
        assert l.size() > 0;  // assert: 我认为这里不可能出错，如果错了说明你代码逻辑写崩了。   开发者断言：既然 l != null，那么它 一定至少包含一个事务，否则逻辑就是错的。

        while(l.size() > 0){
            Long xid = l.remove(0);  // 从等待队列中取出第一个事务
            if(!waitLock.containsKey(xid)){  // 如果这个事务 xid 已经不在 waitLock 中，说明它已经取消等待（比如事务主动中止了），直接跳过。
                continue;
            }else{
                u2x.put(uid,xid);   // 将 uid 分配给 xid
                Lock lo = waitLock.remove(xid);   // 拿到该事务的阻塞锁，不需要等待了
                waitU.remove(xid);  // 删除该事务的等待状态
                lo.unlock();  // 唤醒该事务线程，让它继续执行， 就是
                break;
            }
        }
        if(l.size() == 0) wait.remove(uid);
    }

    /*
    检测当前锁表中是否存在死锁
     */
    private Map<Long, Integer> xidStamp;  // 记录每个事务是否被访问过（stamp是访问编号）
    /*
    类型：Map<事务ID, 整数stamp标记>
    含义：记录每个事务（XID）在 DFS 过程中的访问状态。
     */
    private int stamp;  // 用于 DFS 遍历中给事务标记“访问编号”
    /*
    含义：表示每次 DFS 遍历的唯一标记值
    作用：每次启动一次新的 DFS 检查，就让 stamp++，这样你可以区分不同的 DFS 递归过程。
     */
    // 相当于我们在事务等待图里走一圈，看看是否有环（cycle），有环就意味着死锁。
    /*
    T1 正在等 T2 占用的资源
    T2 正在等 T3 占用的资源
    T3 正在等 T1 占用的资源 ← 形成了环
     */
    private boolean hasDeadLock(){
        xidStamp = new HashMap<>();
        stamp = 1;  // 当前 DFS 的“递归编号”    DFS“第几次”整体递归的编号
        // 这个stamp是用来给本次dfs中所有碰到的xid的一个统一标记，一个dfs递归中所有xid的stamp都一样，借此判断是否有环
        // gpt给你模拟一下过程就懂了
        for(long xid : x2u.keySet()){  // 从每一个正在运行的事务出发（x2u.keySet()）做一次 DFS 遍历
            Integer s = xidStamp.get(xid);
            if(s != null && s > 0){
                continue;  // 已经处理过，不再重复  （这个s如果！= null 且大于0的话，说明之前已经进入过dfs深度遍历，因为只有dfs里面才有xidStamp.put）
            }
            stamp++;  // 每次新的DFS遍历用新编号
            if(dfs(xid)){
                return true; // 检查死锁就是检查是否有环
            }
        }
        return false;
    }

    /*
    死锁检测的核心递归函数
    判断：当前事务 xid 是否在等待图中形成了“等待环”，也就是死锁。
     */
    private boolean dfs(long xid){
        Integer stp = xidStamp.get(xid);  // 当前事务的标签   看看这个事务之前在哪次 DFS 中被访问过
        if(stp != null && stp == stamp){
            return true;  // 当前事务已在本次DFS路径中 ⇒ 出现了环 ⇒ 死锁
        }
        if(stp != null && stp < stamp){
            return false;  // 当前事务是其他DFS路径访问过的，不会影响当前路径 ⇒ 没环
        }

        xidStamp.put(xid,stamp);  // 标记当前事务已在当前路径访问过
        Long uid = waitU.get(xid);   // 查当前事务正在等待哪个资源
        if(uid != null) return false;  // 没等资源，说明不会死锁
        Long x = u2x.get(uid);  // 当前资源被哪个事务持有
        assert x != null;  // 如果事务在等某资源，那这个资源一定是被别人占着的
        return dfs(x);  // 继续 DFS，那个人在等谁？
    }

    /*
    从 listMap 中，找到 key 为 uid0 对应的 List，移除其中的元素 uid1，并在 List 为空时删除整个 key。
     */
    private void removeFromList(Map<Long, List<Long>> listMap, long uid0, long uid1){
        List<Long> l = listMap.get(uid0);
        if(l == null) return;
        Iterator<Long> i = l.iterator();
        while(i.hasNext()){
            Long e = i.next();
            if(e == uid1){
                i.remove();
                break;
            }
        }
        if(l.size() == 0){
            listMap.remove(uid0);
        }
    }

    /*
    就是将uid0放到这个listMap中，同时将uid1放到对应的list中
     */
    private void putIntoList(Map<Long, List<Long>> listMap, long uid0, long uid1){
        if(!listMap.containsKey(uid0)){
            listMap.put(uid0,new ArrayList<>());
        }
        listMap.get(uid0).add(0,uid1);
    }

    /*
    判断uid1是否在这个listMap中uid0对应的list中
     */
    private boolean isInList(Map<Long, List<Long>> listMap, long uid0, long uid1){
        List<Long> l = listMap.get(uid0);
        if(l == null) return false;
        Iterator<Long> i = l.iterator();
        while(i.hasNext()){
            Long e = i.next();
            if(e == uid1){
                return true;
            }
        }
        return false;
    }
}
