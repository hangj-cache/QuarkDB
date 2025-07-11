package com.Hang.backend.TBM;

import com.Hang.backend.DM.DataManager;
import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.VM.VersionManager;
import com.Hang.backend.parser.statement.*;
import com.Hang.backend.utils.Parser;
import com.Hang.common.Error;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TableManagerImpl implements TableManager {

    VersionManager vm;
    DataManager dm;
    private Booter booter;
    private Map<String, Table> tableCache;
    private Map<Long, List<Table>> xidTableCache;
    private Lock lock;

    TableManagerImpl(VersionManager vm, DataManager dm, Booter booter){
        this.vm = vm;
        this.dm = dm;
        this.booter = booter;
        this.tableCache = new HashMap<>();
        this.xidTableCache = new HashMap<>();
        this.lock = new ReentrantLock();
        loadTables();
    }

    private void loadTables(){
        long uid = firstTableUid();
        while(uid != 0){
            Table tb = Table.loadTable(this, uid);
            uid = tb.nextUid;
            tableCache.put(tb.name,tb);
        }
    }

    private long firstTableUid(){
        byte[] raw = booter.load();
        return Parser.parseLong(raw);
    }

    private void updateFirstTableUid(long uid){
        byte[] raw = Parser.long2Byte(uid);
        booter.update(raw);
    }



    @Override
    public BeginRes begin(Begin begin) {
        BeginRes res = new BeginRes();
        int level = begin.isRepeatableRead ? 1 : 0;
        res.xid = vm.begin(level);
        res.result = "begin".getBytes();
        return res;
    }

    @Override
    public byte[] commit(long xid) throws Exception {
        vm.commit(xid);
        return "commit".getBytes();
    }

    @Override
    public byte[] abort(long xid) {
        vm.abort(xid);
        return "abort".getBytes();
    }

    @Override
    public byte[] show(long xid) {
        lock.lock();
        try{
            StringBuilder sb = new StringBuilder();
            for(Table tb : tableCache.values()){
                sb.append(tb.toString()).append("\n");
            }
            List<Table> t = xidTableCache.get(xid);
            if(t == null){
                return "\n".getBytes();
            }
            for(Table tb : t){
                sb.append(tb.toString()).append("\n");
            }
            return sb.toString().getBytes();
        }finally{
            lock.unlock();
        }
    }

    @Override
    public byte[] create(long xid, Create create) throws Exception {
        lock.lock();
        try {
            if(tableCache.containsKey(create.tableName)) {
                throw Error.DuplicatedTableException;
            }
            Table table = Table.createTable(this, firstTableUid(), xid, create);
            updateFirstTableUid(table.uid);
            tableCache.put(create.tableName, table);
            if(!xidTableCache.containsKey(xid)) {
                xidTableCache.put(xid, new ArrayList<>());
            }
            xidTableCache.get(xid).add(table);
            return ("create " + create.tableName).getBytes();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] insert(long xid, Insert insert) throws Exception {
        lock.lock();
        Table table = tableCache.get(insert.tableName);
        lock.unlock();
        if(table == null) {
            throw Error.TableNotFoundException;
        }
        table.insert(xid, insert);
        return "insert".getBytes();
    }

    @Override
    public byte[] read(long xid, Select select) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] update(long xid, Update update) throws Exception {
        return new byte[0];
    }

    @Override
    public byte[] delete(long xid, Delete delete) throws Exception {
        return new byte[0];
    }



}
