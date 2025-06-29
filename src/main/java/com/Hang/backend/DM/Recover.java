package com.Hang.backend.DM;

import com.Hang.backend.DM.dataItem.DataItem;
import com.Hang.backend.DM.logger.Logger;
import com.Hang.backend.DM.page.Page;
import com.Hang.backend.DM.page.PageX;
import com.Hang.backend.DM.pageCache.PageCache;
import com.Hang.backend.TM.TransactionManager;
import com.Hang.backend.common.SubArray;
import com.Hang.backend.utils.Panic;
import com.Hang.backend.utils.Parser;
import com.google.common.primitives.Bytes;

import java.sql.SQLOutput;
import java.util.*;

/**
 * Recover 类负责在数据库启动时执行崩溃恢复操作：通过扫描日志，确保已提交事务的数据被重做（REDO），
 * 未提交事务的数据被撤销（UNDO），从而恢复一致性状态。
 */

public class Recover {
    // 首先定义两种日志的格式 (XID 是事务的 ID，用来标识“一次操作过程”；UID 是记录的唯一 ID，用来标识“一条数据”)
    private static final byte LOG_TYPE_INSERT = 0;
    private static final byte LOG_TYPE_UPDATE = 1;
    // updateLog: [LogType] [XID] [UID] [OldRaw] [NewRaw]
    // insertLog: [LogType] [XID] [Pgno] [offset] [Raw]

    // 标记是重做还是撤销  已完成事务的就重做，未完成事务的就撤销   事务有两种完成形式：提交和回滚，事务完成了可能是提交或者回滚了，提交了的事务就是完成的事务
    // 回滚只能作用于尚未提交的事务（也就是“进行中的事务”）
    private static final int REDO = 0;
    private static final int UNDO = 1;


    static class InsertLogInfo{
        long xid;
        int pgno;
        short offset;
        byte[] raw;
    }

    static class UpdateLogInfo{
        long xid;
        int pgno;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }


    public static void recover(TransactionManager tm, Logger lg, PageCache pc){
        System.out.println("Recoverint...");

        lg.rewind();  // 日志文件的前四个字节是这个日志文件的校验和，后面是一个一个独立的日志
        int maxPgno = 0;
        while(true){
            byte[] log = lg.next();
            if(log == null) break;
            int pgno;
            if(isInsertLog(log)){
                InsertLogInfo li = parseInsertLog(log);
                pgno = li.pgno;
            }else{
                UpdateLogInfo li = parseUpdateLog(log);
                pgno = li.pgno;
            }
            if(pgno > maxPgno){
                maxPgno = pgno;
            }
        }

        if(maxPgno == 0){
            maxPgno = 1;
        }

        pc.truncateByPgno(maxPgno);  // 这是进行崩溃恢复，就是恢复到和日志一样的状态
        System.out.println("Truncate to " + maxPgno + " pages.");
        redoTranscation(tm,lg,pc);
        System.out.println("Redo Transaction Over.");
        undoTranscation(tm,lg,pc);
        System.out.println("Undo Transaction Over.");

        System.out.println("Recovery Over.");
    }

    // recover要做的主要就是两步：重做所有已完成事务，撤销所有未完成事务
    private static void redoTranscation(TransactionManager tm, Logger lg, PageCache pc){
        lg.rewind();
        while(true){
            byte[] log = lg.next();
            if(log == null) break;
            if(isInsertLog(log)){
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                if(!tm.isActive(xid)){
                    doInsertLog(pc,log,REDO);
                }
            }else{
                UpdateLogInfo li = parseUpdateLog(log);
                long xid = li.xid;
                if(!tm.isActive(xid)){
                    doInsertLog(pc,log,REDO);
                }
            }
        }
    }

    /*
    收集所有未提交（active）事务的日志，缓存进内存，准备倒序地执行 UNDO 操作。
    这里只是进行收集
     */
    private static void undoTranscation(TransactionManager tm, Logger lg, PageCache pc){
        Map<Long, List<byte[]>> logCache = new HashMap<>();
        lg.rewind();  // 这就是把position指针移到4位置，就是跳过校验和
        while(true){
            byte[] log = lg.next();
            if(log == null) break;
            if(isInsertLog(log)){
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                if(tm.isActive(xid)){
                    if(!logCache.containsKey(xid)){
                        logCache.put(xid,new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            }else{
                UpdateLogInfo li = parseUpdateLog(log);
                long xid = li.xid;
                if(tm.isActive(xid)){
                    if(!logCache.containsKey(xid)){
                        logCache.put(xid,new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            }
        }

        // 开始对所有active log 进行倒序undo
        for(Map.Entry<Long,List<byte[]>> entry : logCache.entrySet()){
            List<byte[]> logs = entry.getValue();
            for (int i = logs.size()-1; i >= 0; i--) {
                byte[] log = logs.get(i);
                if(isInsertLog(log)){
                    doInsertLog(pc,log,UNDO);
                }else{
                    doUpdateLog(pc,log,UNDO);
                }
            }
        }
    }

    private static boolean isInsertLog(byte[] log){
        return log[0] == LOG_TYPE_INSERT; // 日志中第一个字节用来判断是插入还是更新
    }

    // updateLog: [LogType] [XID] [UID] [OldRaw] [NewRaw]
    private static final int OF_TYPE = 0;
    private static final int OF_XID = OF_TYPE + 1;
    private static final int OF_UPDATE_UID = OF_XID + 8;
    private static final int OF_UPDATE_RAW = OF_UPDATE_UID + 8;

    public static byte[] updateLog(long xid, DataItem di){  // 这就是将这个操作组装成一个updateLog
        byte[] logType = {LOG_TYPE_UPDATE};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] uidRaw = Parser.long2Byte(di.getUid());
        byte[] oldRaw = di.getOldRaw();
        SubArray raw = di.getRaw(); // SubArray代表的是子数组，里面又raw、start以及end
        byte[] newRaw = Arrays.copyOfRange(raw.raw,raw.start,raw.end);
        return Bytes.concat(logType,xidRaw,uidRaw,oldRaw,newRaw);
    }

    private static UpdateLogInfo parseUpdateLog(byte[] log){  // 先要清楚log这个逻辑日志的结构
        UpdateLogInfo li = new UpdateLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log,OF_XID,OF_UPDATE_UID));
        long uid = Parser.parseLong(Arrays.copyOfRange(log,OF_UPDATE_UID,OF_UPDATE_RAW));  // uid 本身就是 8 字节，就是64位的
        li.offset = (short)(uid & ((1L << 16) - 1)); // 取低 16 位。因为uid本身就是short类型，2个字节，16位
        uid >>>= 32; // 把 uid 这个 long 类型的数向右无符号移动 32 位，保留高 32 位，丢弃低 32 位。
        li.pgno = (int)(uid & ((1L << 32) - 1)); // 取高 32 位
        // 低 16 位是 offset（槽位号），高 32 位是页号（pgno）
        int length = (log.length - OF_UPDATE_RAW) / 2;  // 这是oldRaw + newRaw数据的长度  raw就是原始的字节数据（raw bytes）
        // 这里除以2了，因此[OldRaw] [NewRaw]是一样长的，并且挨着的
        li.oldRaw = Arrays.copyOfRange(log,OF_UPDATE_RAW,OF_UPDATE_RAW+length);
        li.newRaw = Arrays.copyOfRange(log,OF_UPDATE_RAW+length,OF_UPDATE_RAW + 2 * log.length);
        return li;
    }

    private static void doUpdateLog(PageCache pc, byte[] log, int flag){  // 这就开始执行重或者撤销操作了
        int pgno;
        short offset;
        byte[] raw;
        if(flag == REDO){
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.newRaw;  // newRaw就是获得的新值
        }else{
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.oldRaw;  // oldRaw就是原始的旧值
        }

        Page pg = null;
        try{
            pg = pc.getPage(pgno);
        }catch (Exception e){
            Panic.panic(e);
        }

        try{
            PageX.recoverUpdate(pg,raw,offset);
        }finally{
            pg.release();
        }
    }

    // 就是照这个定义的：insertLog: [LogType]1 [XID]8 [Pgno]8 [Offset]2 [Raw]8
    private static final int OF_INSERT_PGNO = OF_XID + 8;
    private static final int OF_INSERT_OFFSET = OF_INSERT_PGNO + 4;
    private static final int OF_INSERT_RAW = OF_INSERT_OFFSET + 2;


    public static byte[] insertLog(long xid, Page pg, byte[] raw){  // insertLog和updateLog不一样，updateLog设计的是旧数据和新数据
        // updateLog则是插入一条数据，应该是页数据Page
        byte[] logTypeRaw = {LOG_TYPE_INSERT};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] pgnoRaw = Parser.int2Byte(pg.getPageNumber());
        short offset = PageX.getFSO(pg);
        byte[] offsetRaw = Parser.short2Byte(offset);
        return Bytes.concat(logTypeRaw,xidRaw,pgnoRaw,offsetRaw,raw); // 这是往page中要插入这个raw
    }


    private static InsertLogInfo parseInsertLog(byte[] log){
        InsertLogInfo li = new InsertLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log,OF_XID,OF_INSERT_PGNO));
        li.pgno = Parser.parseInt(Arrays.copyOfRange(log,OF_INSERT_PGNO,OF_INSERT_OFFSET));
        li.offset = Parser.parseShort(Arrays.copyOfRange(log,OF_INSERT_OFFSET,OF_INSERT_RAW));
        li.raw = Arrays.copyOfRange(log,OF_INSERT_RAW,log.length);
        return li;
    }

    private static void doInsertLog(PageCache pc, byte[] log, int flag){
        InsertLogInfo li = parseInsertLog(log);
        Page pg = null;
        try{
            pg = pc.getPage(li.pgno);
        }catch (Exception e){
            Panic.panic(e);
        }

        try{
            if(flag == UNDO){
                DataItem.setDataItemRawInvalid(li.raw);  // 这个只是设置这个dataItem的raw是否有效，如果是撤销的话就设置这个原始数据raw无效即可
            }
            PageX.recoverInsert(pg,li.raw,li.offset);  // 不管redo还是undo都是要执行这部分的
        }finally{
            pg.release();
        }
    }


}
