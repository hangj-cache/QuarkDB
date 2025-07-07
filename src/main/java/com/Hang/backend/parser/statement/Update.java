package com.Hang.backend.parser.statement;

public class Update {
    public String tableName;  // 更新的表名
    public String fieldName;  // 更新的字段名
    public String value;  // 要更新的值
    public Where where; // 条件
}
