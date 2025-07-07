package com.Hang.backend.utils;


public class ParseStringRes {
    public String str;  // 解析出来的字符串（如表名或字段名） fieldName
    public int next;  // 这个字符串在原始字节数据中占用了多少字节

    public ParseStringRes(String str, int next) {
        this.str = str;
        this.next = next;
    }
}
