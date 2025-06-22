package com.Hang.backend.utils;

/**
 * 经典的一个 地址编码函数
 * 通过将页号（pgno）和槽位号（offset）合并成一个 long 类型的唯一标识符（UID）
 *
 * 将一个数据项的位置 —— 包括：
 * 页号 pgno（表示在哪个 Page 中）
 * 页内偏移量 offset（表示在 Page 内哪个槽位）
 * 编码成一个 long 类型的 UID，用于唯一标识一条记录的位置。
 */
public class Types {
    public static long addressToUid(int pgno, short offset) {
        long u0 = (long)pgno;
        long u1 = (long)offset;
        return u0 << 32 | u1;
    }
}

