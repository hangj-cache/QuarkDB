package com.Hang.backend.TBM;

import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.parser.statement.*;
import com.Hang.backend.utils.Panic;
import com.Hang.backend.utils.ParseStringRes;
import com.Hang.backend.utils.Parser;
import com.google.common.primitives.Bytes;
import com.Hang.common.Error;

import java.util.*;

/**
 * Table维护了表结构
 * 二进制结构如下：
 * [TableName][NextTable]
 * [Field1Uid][Field2Uid]...[FieldNUid]
 *
 * Table 类表示一张表的元数据和数据操作行为，它负责表结构的持久化、加载和增删查改等操作，是数据库中对表的完整抽象。
 */
public class Table {
    TableManager tbm;  // 表管理器，用于操作版本管理器等
    long uid;  // 表的唯一ID（元数据在磁盘上的位置）
    String name;  // 表名
    byte status;  // 表状态（未使用，可扩展）
    long nextUid;
    /*
    table表中的nextUid 是 当前表的“下一个表”的 UID，也就是指向下一张表的磁盘位置，从而构成一个链表式的表目录。
    [user] → [order] → [product]  你只要知道第一张表的 UID（比如保存在 Booter 文件中），就可以顺着 nextUid 依次加载所有表。
    用于表目录管理以及动态添加表，恢复时还原所有表
     */
    List<Field> fields = new ArrayList<>();   // 表字段列表，每个字段对应一列， 表明这个表中所有字段


    public static Table loadTable(TableManager tbm, long uid){
        byte[] raw = null;
        try{
            raw = ((TableManagerImpl)tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        }catch (Exception e){
            Panic.panic(e);
        }
        assert raw != null;
        Table tb = new Table(tbm, uid);
        return tb.parseSelf(raw);
    }

    /*
    用于创建一张表，包含：
    构建字段信息（Field 对象）
    处理索引字段
    调用 persistSelf() 把元数据持久化
     */
    public static Table createTable(TableManager tbm, long nextUid, long xid, Create create) throws Exception{
        Table tb = new Table(tbm, create.tableName, nextUid);  // 创建表对象（还未持久化）
        for (int i = 0; i < create.fieldName.length; i++) {  // 对一个表中的所有字段进行遍历
            String fieldName = create.fieldName[i];
            String fieldType = create.fieldType[i];
            boolean indexed = false;
            for(int j = 0; j < create.index.length; j++){  // 遍历索引数组，检查是否需要索引
                if(fieldName.equals(create.index[j])){
                    indexed = true;  // 只要有一个字段建立了索引，这个表就是建立了索引
                    break;
                }
            }
            tb.fields.add(Field.createField(tb,xid,fieldName,fieldType,indexed)); // 创建字段对象，并添加进表
        }
        return tb.persistSelf(xid);  // 持久化整个表结构
    }


    public Table(TableManager tbm, long uid){
        this.tbm = tbm;
        this.uid = uid;
    }

    public Table(TableManager tbm, String tableName, long nextUid){
        this.tbm = tbm;
        this.name = tableName;
        this.nextUid = nextUid;
    }

    /**
     * 将从磁盘读出来的一段原始字节数据反序列化为一个 Table 对象的完整内存结构，也就是“加载表结构”。
     * 就是从磁盘中读取出来的二进制数据恢复成Table这样的类结构
     */
    private Table parseSelf(byte[] raw){
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        name = res.str;
        position += res.next;
        nextUid = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
        position += 8;

        while(position < raw.length){
            long uid = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
            position += 8;
            fields.add(Field.loadField(this,uid));
        }
        return this;
    }

    /**
     * 用于将 Table 对象持久化保存到磁盘
     * 将Table对象持久化的磁盘需要将表名、下一张表的UID，以及将每一个字段的uid concat起来然后一起Bytes.concat起来存到磁盘里
     */
    private Table persistSelf(long xid) throws Exception {
        byte[] nameRaw = Parser.string2Byte(name);  // 把表名（如 "user"）转换成字节数组，用于存入磁盘。
        byte[] nextRaw = Parser.long2Byte(nextUid);  // 把当前表的 nextUid（指向“下一张表”的 UID）转成字节数组，作为元数据的一部分写入磁盘。
        byte[] fieldRaw = new byte[0];
        for(Field field : fields) {  // 把当前表的所有字段的 UID 逐个转成字节，并拼接成一个整体字节流。
            fieldRaw = Bytes.concat(fieldRaw, Parser.long2Byte(field.uid));
        }
        uid = ((TableManagerImpl)tbm).vm.insert(xid, Bytes.concat(nameRaw, nextRaw, fieldRaw));
        return this;
    }
    public int update(long xid, Update update) throws Exception {
        List<Long> uids = parseWhere(update.where);
        Field fd = null;
        for (Field f : fields) {
            if(f.fieldName.equals(update.fieldName)) {
                fd = f;
                break;
            }
        }
        if(fd == null) {
            throw Error.FieldNotFoundException;
        }
        Object value = fd.string2Value(update.value);
        int count = 0;
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl)tbm).vm.read(xid, uid);
            if(raw == null) continue;

            ((TableManagerImpl)tbm).vm.delete(xid, uid);

            Map<String, Object> entry = parseEntry(raw);
            entry.put(fd.fieldName, value);
            raw = entry2Raw(entry);
            long uuid = ((TableManagerImpl)tbm).vm.insert(xid, raw);

            count ++;

            for (Field field : fields) {
                if(field.isIndexed()) {
                    field.insert(entry.get(field.fieldName), uuid);
                }
            }
        }
        return count;
    }

    public String read(long xid, Select read) throws Exception {
        List<Long> uids = parseWhere(read.where);
        StringBuilder sb = new StringBuilder();
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl)tbm).vm.read(xid, uid);
            if(raw == null) continue;
            Map<String, Object> entry = parseEntry(raw);
            sb.append(printEntry(entry)).append("\n");
        }
        return sb.toString();
    }

    public void insert(long xid, Insert insert) throws Exception {
        Map<String, Object> entry = string2Entry(insert.values);
        byte[] raw = entry2Raw(entry);
        long uid = ((TableManagerImpl)tbm).vm.insert(xid, raw);
        for (Field field : fields) {
            if(field.isIndexed()) {
                field.insert(entry.get(field.fieldName), uid);
            }
        }
    }

    private Map<String, Object> string2Entry(String[] values) throws Exception {
        if(values.length != fields.size()) {
            throw Error.InvalidValuesException;
        }
        Map<String, Object> entry = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Object v = f.string2Value(values[i]);
            entry.put(f.fieldName, v);
        }
        return entry;
    }

    private List<Long> parseWhere(Where where) throws Exception {
        long l0=0, r0=0, l1=0, r1=0;
        boolean single = false;
        Field fd = null;
        if(where == null) {
            for (Field field : fields) {
                if(field.isIndexed()) {
                    fd = field;
                    break;
                }
            }
            l0 = 0;
            r0 = Long.MAX_VALUE;
            single = true;
        } else {
            for (Field field : fields) {
                if(field.fieldName.equals(where.singleExp1.field)) {
                    if(!field.isIndexed()) {
                        throw Error.FieldNotIndexedException;
                    }
                    fd = field;
                    break;
                }
            }
            if(fd == null) {
                throw Error.FieldNotFoundException;
            }
            CalWhereRes res = calWhere(fd, where);
            l0 = res.l0; r0 = res.r0;
            l1 = res.l1; r1 = res.r1;
            single = res.single;
        }
        List<Long> uids = fd.search(l0, r0);
        if(!single) {
            List<Long> tmp = fd.search(l1, r1);
            uids.addAll(tmp);
        }
        return uids;
    }

    class CalWhereRes {
        long l0, r0, l1, r1;
        boolean single;
    }

    private CalWhereRes calWhere(Field fd, Where where) throws Exception {
        CalWhereRes res = new CalWhereRes();
        switch(where.logicOp) {
            case "":
                res.single = true;
                FieldCalRes r = fd.calExp(where.singleExp1);
                res.l0 = r.left; res.r0 = r.right;
                break;
            case "or":
                res.single = false;
                r = fd.calExp(where.singleExp1);
                res.l0 = r.left; res.r0 = r.right;
                r = fd.calExp(where.singleExp2);
                res.l1 = r.left; res.r1 = r.right;
                break;
            case "and":
                res.single = true;
                r = fd.calExp(where.singleExp1);
                res.l0 = r.left; res.r0 = r.right;
                r = fd.calExp(where.singleExp2);
                res.l1 = r.left; res.r1 = r.right;
                if(res.l1 > res.l0) res.l0 = res.l1;
                if(res.r1 < res.r0) res.r0 = res.r1;
                break;
            default:
                throw Error.InvalidLogOpException;
        }
        return res;
    }

    private String printEntry(Map<String, Object> entry) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            sb.append(field.printValue(entry.get(field.fieldName)));
            if(i == fields.size()-1) {
                sb.append("]");
            } else {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private Map<String, Object> parseEntry(byte[] raw) {
        int pos = 0;
        Map<String, Object> entry = new HashMap<>();
        for (Field field : fields) {
            Field.ParseValueRes r = field.parserValue(Arrays.copyOfRange(raw, pos, raw.length));
            entry.put(field.fieldName, r.v);
            pos += r.shift;
        }
        return entry;
    }

    private byte[] entry2Raw(Map<String, Object> entry) {
        byte[] raw = new byte[0];
        for (Field field : fields) {
            raw = Bytes.concat(raw, field.value2Raw(entry.get(field.fieldName)));
        }
        return raw;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append(name).append(": ");
        for(Field field : fields) {
            sb.append(field.toString());
            if(field == fields.get(fields.size()-1)) {
                sb.append("}");
            } else {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

}
