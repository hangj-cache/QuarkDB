package com.Hang.backend.TBM;

import com.Hang.backend.IM.BPlusTree;
import com.Hang.backend.TM.TransactionManagerImpl;
import com.Hang.backend.parser.statement.SingleExpression;
import com.Hang.backend.utils.Panic;
import com.Hang.backend.utils.ParseStringRes;
import com.Hang.backend.utils.Parser;
import com.Hang.common.Error;
import com.google.common.primitives.Bytes;

import java.util.Arrays;
import java.util.List;

public class Field {
    long uid;
    private Table tb;
    String fieldName;
    String fieldType;
    private long index;
    private BPlusTree bt;

    public static Field loadField(Table tb, long uid){
        byte[] raw = null;
        try{
            raw = ((TableManagerImpl) tb.tbm).vm.read(TransactionManagerImpl.SUPER_XID,uid);
        }catch(Exception e){
            Panic.panic(e);
        }
        assert raw != null;
        return new Field(uid,tb).parseSelf(raw);
    }

    public Field(long uid, Table tb){
        this.uid = uid;
        this.tb = tb;
    }

    public Field(Table tb, String fieldName, String fieldType, long index){
        this.tb = tb;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.index = index;
    }

    /*
     ParseStringRes 类是一个 字符串解析结果的包装类，用于从原始字节数据中解析出字符串时，同时返回解析出的字符串和它占用的字节长度。
     假设字段为：name:String 且有索引，字节结构可能为：
     raw结构：[字段名长度+内容][字段类型长度+内容][8字节索引UID]   这是一块一块的，分成三部分

     最终解析出来即使一个字段
     */
    private Field parseSelf(byte[] raw){  // 一个raw表示一个字段的信息
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        // 这里虽然是传入一整个raw，其实真正解析的只有前4个字节(解析出长度，然后根据这个长度解析出对应的内容)这个是字段名的部分
        // 因此不是所有都去解析的
        fieldName = res.str;
        position += res.next;  // 这是将指针移到下一个字段，以及内容
        res = Parser.parseString(Arrays.copyOfRange(raw,position,raw.length)); // 字段类型的部分
        fieldType = res.str;
        position += res.next;
        this.index = Parser.parseLong(Arrays.copyOfRange(raw,position,position+8));
        if(index != 0){
            try{
                bt = BPlusTree.load(index,((TableManagerImpl)tb.tbm).dm);
            }catch(Exception e){
                Panic.panic(e);
            }
        }
        return this;
    }

    /*
    在创建新表时，创建字段 Field 对象，并将字段元信息持久化（写入磁盘）。
    如果字段开启了索引，还会创建对应的 B+ 树。
     */
    public static Field createField(Table tb, long xid, String fieldName, String fieldType, boolean indexed) throws Exception {
        typeCheck(fieldType);
        Field f = new Field(tb, fieldName, fieldType, 0);
        if(indexed) {
            long index = BPlusTree.create(((TableManagerImpl)tb.tbm).dm);
            BPlusTree bt = BPlusTree.load(index, ((TableManagerImpl)tb.tbm).dm);
            f.index = index;
            f.bt = bt;
        }
        f.persistSelf(xid);
        return f;
    }

    private void persistSelf(long xid) throws Exception{
        byte[] nameRaw = Parser.string2Byte(fieldName);
        byte[] typeRaw = Parser.string2Byte(fieldType);
        byte[] indexRaw = Parser.long2Byte(index);
        this.uid = ((TableManagerImpl)tb.tbm).vm.insert(xid, Bytes.concat(nameRaw, typeRaw, indexRaw));
    }

    private static void typeCheck(String fieldType) throws Exception{
        if(!"int32".equals(fieldType) && !"int64".equals(fieldType) && !"String".equals(fieldType)){
            throw Error.InvalidFieldException;
        }
    }

    public boolean isIndexed(){
        return index != 0;
    }

    public void insert(Object key, long uid) throws Exception{
        long uKey = value2Uid(key);
        bt.insert(uKey,uid);
    }

    public List<Long> search(long left, long right) throws Exception{
        return bt.searchRange(left,right);
    }

    public Object string2Value(String str){
        switch(fieldType){
            case "int32":
                return Integer.parseInt(str);
            case "int64":
                return Long.parseLong(str);
            case "string":
                return str;
        }
        return null;
    }

    public long value2Uid(Object key){
        long uid = 0;
        switch(fieldType){
            case "string":
                uid = Parser.str2Uid((String) key);
                break;
            case "int32":
                int uint = (int)key;
                return (long)uint;
            case "int64":
                uid = (long)key;
                break;
        }
        return uid;
    }

    public byte[] value2Raw(Object v) {
        byte[] raw = null;
        switch(fieldType) {
            case "int32":
                raw = Parser.int2Byte((int)v);
                break;
            case "int64":
                raw = Parser.long2Byte((long)v);
                break;
            case "string":
                raw = Parser.string2Byte((String)v);
                break;
        }
        return raw;
    }

    class ParseValueRes {
        Object v;
        int shift;
    }

    public ParseValueRes parserValue(byte[] raw) {
        ParseValueRes res = new ParseValueRes();
        switch(fieldType) {
            case "int32":
                res.v = Parser.parseInt(Arrays.copyOf(raw, 4));
                res.shift = 4;
                break;
            case "int64":
                res.v = Parser.parseLong(Arrays.copyOf(raw, 8));
                res.shift = 8;
                break;
            case "string":
                ParseStringRes r = Parser.parseString(raw);
                res.v = r.str;
                res.shift = r.next;
                break;
        }
        return res;
    }

    public String printValue(Object v) {
        String str = null;
        switch(fieldType) {
            case "int32":
                str = String.valueOf((int)v);
                break;
            case "int64":
                str = String.valueOf((long)v);
                break;
            case "string":
                str = (String)v;
                break;
        }
        return str;
    }

    @Override
    public String toString() {
        return new StringBuilder("(")
                .append(fieldName)
                .append(", ")
                .append(fieldName)
                .append(index != 0 ? ", Index" : ", NoIndex")
                .append(")")
                .toString();
    }

    public FieldCalRes calExp(SingleExpression exp) throws Exception {
        Object v = null;
        FieldCalRes res = new FieldCalRes();
        switch(exp.compareOp) {
            case "<":
                res.left = 0;
                v = string2Value(exp.value);
                res.right = value2Uid(v);
                if(res.right > 0) {
                    res.right --;
                }
                break;
            case "=":
                v = string2Value(exp.value);
                res.left = value2Uid(v);
                res.right = res.left;
                break;
            case ">":
                res.right = Long.MAX_VALUE;
                v = string2Value(exp.value);
                res.left = value2Uid(v) + 1;
                break;
        }
        return res;
    }
}

