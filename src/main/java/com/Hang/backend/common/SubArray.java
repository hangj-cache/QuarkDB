package com.Hang.backend.common;

/**
 * DataItem 的数据内容通过 SubArray 对象返回给上层模块，
 * 这使得上层模块可以直接访问数据内容而无需进行拷贝。
 *
 * 之所以要定义这样一个SubArray是因为java是高级语言，就是如果直接用new来创建子数组，那么在底层就会重新新建一块内存
 * 最后操作的数据是分离的数据，这里用SubArray一来是就设置raw，这样此后就操作的是同一块内存了
 */
public class SubArray {
    public byte[] raw;  // 原始数据
    public int start;
    public int end;

    public SubArray(byte[] raw, int start, int end) {
        this.raw = raw;
        this.start = start;
        this.end = end;
    }
}
