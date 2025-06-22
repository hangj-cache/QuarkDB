package com.Hang.backend.DM.pageIndex;

import com.Hang.backend.DM.pageCache.PageCache;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageIndex {
    // 将一页划分成40个区间
    private static final int INTERVALS_NO = 40;  // interval : 间隔
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;  // 这是平均每一个区间的大小(阈值)

    /*
    把一个页的可用空间平均分成 40 段（INTERVALS_NO = 40），每段大小是 THRESHOLD = PageCache.PAGE_SIZE / 40 字节。
    然后根据页的空闲空间落在哪段，就放进 lists[number] 中，支持分段查找 + 快速插入页选择。
     */

    private Lock lock;
    private List<PageInfo>[] lists;  // 这是一个数组，每个元素是一个list集合

    @SuppressWarnings("unchecked") // 作用是：告诉编译器“我知道我在干什么”，请不要对这一行代码产生“泛型未经检查的类型转换”警告。
    // 在 Java 中，不允许直接创建泛型数组。
    // Java 的泛型是 擦除实现的（type erasure），运行时根本无法知道 List<Integer> 还是 List<String>，所以会警告你：“可能发生类型不安全的操作”。
    public PageIndex() {
        lock = new ReentrantLock();
        lists = new List[INTERVALS_NO + 1]; // 这是一个数组
        for(int i = 0; i < INTERVALS_NO + 1; i++) {
            lists[i] = new LinkedList<>();  // 数组中每一个元素都是一个linkedlist集合，链表是空闲空间落在这个区间的页码以及空闲空间大小(PageInfo)
        }
    }

    public void add(int pgno, int freeSpace){  // 将某个页（pgno）当前剩余的空间（freeSpace）加入空闲页列表中，用于后续插入选择。
        lock.lock();
        try{
            int number = freeSpace / THRESHOLD;  // 将不同空闲度的页放到了对应的 lists[number] 里，就是说那个页空闲空间多，number越大，定位数组位置越靠后
            lists[number].add(new PageInfo(pgno, freeSpace));
            // 数组 lists[] 中存的是一组链表，每个链表中记录的是「哪些页 pgno 还有多少 freeSpace」，封装在 PageInfo 对象中，用于空闲页的快速查找与管理。
        }finally{
            lock.unlock();
        }
    }

    public PageInfo select(int spaceSize){  // 根据待插入数据的大小（spaceSize），选择一个能容纳它的页号，从 freelist 中返回。
        lock.lock();
        try{
            int number = spaceSize / THRESHOLD;
            if(number < INTERVALS_NO) number++;  // 防守式提升一个空闲度（更保险）因为100/25=4 同时101/25=4一样的，因此直接变大一个保险直接5*25=125
            while(number < INTERVALS_NO){
                if(lists[number].size() == 0){ // 这就是这个区间没有合适的页，则number变大，找有更大空间的页
                    number++;
                    continue;
                }
                return lists[number].remove(0);  // 移除并返回第一个元素，也就是第一个页信息，PageInfo
            }
            return null;
        }finally{
            lock.unlock();
        }
    }
}
