package com.Hang.backend.TBM;

/*
BeginRes 很可能是某个“开始操作”的返回结果（Begin Result 的缩写），比如：
事务开始（begin transaction）时的返回信息
某个会话或操作的初始化响应
 */
public class BeginRes {
    public long xid;
    public byte[] result;
}
