package com.Hang.backend.common;

/**
 * DataItem 的数据内容通过 SubArray 对象返回给上层模块，
 * 这使得上层模块可以直接访问数据内容而无需进行拷贝。
 *
 * 之所以要定义这样一个SubArray是因为java是高级语言，数组在底层是以对象方式存在的，
 * 就是如果直接用new来创建子数组，那么在底层就会重新新建一块内存，就是subArray这样的，都是新创建一个对象，而不是在原对象上操作的
 * 最后操作的数据是分离的数据，这里用SubArray一来是就设置raw，这样此后就操作的是同一块内存了
 */

/**
 * SubArray 是数据库中处理「页中一段数据」的轻量抽象，start 和 end 用于 定位页中某个数据项的边界，避免不必要的字节数组复制。
 * 就相当于一个数据页有很多个dataItem，就可以用这个来表示各个dataItem
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
