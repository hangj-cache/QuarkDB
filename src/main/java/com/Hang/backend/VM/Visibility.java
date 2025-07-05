package com.Hang.backend.VM;


import com.Hang.backend.TM.TransactionManager;

/**
 * 实现 MVCC（多版本并发控制） 中的“可见性判断”逻辑，也就是判断某条数据 Entry 是否对某个事务可见。
 *
 * 背景知识：为什么需要可见性判断？
 * 在多事务并发执行时，不同事务对数据的“可见性”可能不同。例如：
 * 事务 A 插入了一条数据还没提交，事务 B 应该看不到。
 * 事务 C 删除了一条数据，事务 D 在执行时也可能看不到，取决于事务隔离级别。
 * 所以，每条记录 Entry 里都会有：
 * xmin：插入它的事务 ID
 * xmax：删除它的事务 ID（如果还没被删除就是 0）
 */

/*
 注意注意：事务隔离级别指的都是这条记录的一些限制，记住是读取这条记录
 */
public class Visibility {

    /*
    判断当前事务是否跳过了某个版本的可见性，违反了事务隔离性原则。
    适用于 可重复读（Repeatable Read） 的隔离级别。
    isVersionSkip(...) 是用来判断当前事务 t 在访问一个数据版本 Entry e 时，是否发生了所谓的 “版本跳跃（Version Skip）” 的问题

    事务控制数据项的创建和删除，而每个数据项记录着它被哪个事务创建和删除的“历史”，用来做可见性判断。
     */

    /*
    版本跳跃是指：在一个事务中，两次读取同一条记录，第一次读到了旧版本，第二次却读到了新版本（或发现它已被删）。
    就是读取两次数据不一样，就是不可重复度问题
    而读已提交是允许出现不可重复读问题的，可重复读才需要进行避免

    XMIN：插入该数据的事务 ID（创建者）
    XMAX：删除该数据的事务 ID（删除者）
     */
    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e){
        long xmax = e.getXmax();
        if(t.level == 0){
            return false;  // Read Committed 模式下，允许版本跳跃
        }else{
            // 只有在 可重复读（RR）模式下才检查版本跳跃---且只有在删除者已经提交的情况下才算是版本跳跃
            // 删除者的事务 ID 比当前事务大，说明是 后来提交的删除或者删除者事务还没提交，在当前事务快照中被认为是 未提交 的，也要避免
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
            // tm.isCommitted(xmax)指的是事务 xmax 是否已提交（系统全局状态）
            // t.isInSnapshot(xmax)指的是事务 xmax 在当前事务开始时是否未提交（当前事务的视角快照）
            // 调用的是当前事务 t 的快照方法，意思是：“在我事务 t 开始时，事务 xmax 是不是还没提交？”  在快照中就是没提交

            /*
            整体意思解读：
            如果这个记录是被一个提交了的事务删的（isCommitted(xmax)）
            并且这个删除事务是在我之后开始的或在我的快照里还未提交（xmax > t.xid || isInSnapshot(xmax)）
            那我就不应该看见这个数据 ⇒ 版本跳跃
             */
        }
    }

    /*
    主入口，判断某条记录是否对事务可见：
     */
    public static boolean isVisible(TransactionManager tm, Transaction t,Entry e){
        if(t.level == 0){
            return readCommitted(tm, t, e);
        }else{
            return repeatableRead(tm, t, e);
        }
    }

    /*
    读已提交
     */
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e){
        long xid = t.xid;  // t指的是当前的事务
        // xid指的是当前要读这条记录的事务的 ID
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if(xmin == xid && xmax == 0){ // 当前事务自己插入没有被删除当然是可见的
            return true;
        }
        // 说明插入这条记录的事务已经提交了。
        if(tm.isCommitted(xmin)){  // 检查这个是否已提交就是tm事务管理中检查这个一个字节的字段是否和对应的字段相等即可
            if(xmax == 0) return true;  // 没被删除，当然可以看见。
            if(xmax != xid){  // 被别的事务删除了。
                if(!tm.isCommitted(xmax)){  // 删除它的那个事务还没提交 ⇒ 删除不算数 ⇒ 这条记录仍然可以看见！
                    // （读已提交顾名思义就是只读取已提交的数据）
                    return true;
                }
            }
        }
        return false;
    }

    /*
    可重复读（更严格）
     */
    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e){
        long xid = t.xid;  // t指的是当前事务
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if(xmin == xid && xmax == 0) return true;  // 当前事务自己插入的记录，且没有被删除 → 可见

        if(tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)){  //插入者已经提交 ✅ 插入者在当前事务开始之前（xmin < xid）✅ 插入者不在当前事务快照中 ✅（说明插入时已提交）
            if(xmax == 0) return true;
            if(xmax != xid){
                if(!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)){  // 在当前事务快照中就是没有提交-- 可见
                    return true;
                }
            }
        }
        return false;
    }

}
