package com.Hang.backend.parser.statement;

/**
 * 查询的表名
 * 查询的字段
 *
 */
public class Select {
    public String tableName; // 查询的表名
    public String[] fields;  // 要查询的字段名列表，支持 * 或多个字段
    public Where where;  // 可选的查询条件，表示 WHERE 子句结构
}
