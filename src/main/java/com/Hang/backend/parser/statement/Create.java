package com.Hang.backend.parser.statement;

/**
 * 例子
 * {
 *   "tableName": "user",
 *   "fieldName": ["id", "name", "age"],
 *   "fieldType": ["int32", "string", "int32"],
 *   "index": ["id", "age"]
 * }
 */
public class Create {
    public String tableName;  // 表名
    public String[] fieldName;  // 字段名
    public String[] fieldType;  // 字段类型
    public String[] index;  // 用来指定哪些字段要建立索引，从而加速查询效率。
}
