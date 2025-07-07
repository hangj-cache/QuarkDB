package com.Hang.backend.parser.statement;

/**
 * 例子：WHERE age > 18 AND gender = 'male'，可以解析为如下
 * singleExp1: age > 18
 * logicOp: "AND"
 * singleExp2: gender = 'male'
 *
 * Where where = new Where();
 * where.singleExp1 = new SingleExpression("age", ">", "18");
 * where.logicOp = "AND";
 * where.singleExp2 = new SingleExpression("gender", "=", "'male'");
 */
public class Where {
    public SingleExpression singleExp1;  // 第一部分条件表达式
    public String logicOp;  // 逻辑运算符（如 "AND" / "OR"）
    public SingleExpression singleExp2;  // 第二部分条件表达式
}
