package com.Hang.backend.DM.pageCache;


import com.Hang.backend.DM.page.Page;
import com.Hang.backend.utils.Panic;
import com.Hang.common.Error;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * PageCache和Page又不一样，Page主要是页面属性，PageCache主要包含页面缓存的属性（页面的一般性质，磁盘+缓存都有的性质），
 * 页面接口针对页面的一些方法，页面缓存接口则是缓存层面的（缓存中的页数据特有属性）（要刷盘）
 *
 * 定义页面缓存的接口，包括新建页面、获取页面、
 * 释放页面、关闭缓存、根据最大页号截取缓存、
 * 获取当前页面数量以及刷新页面等方法
 *
 * 接口中的静态方法可以直接看成本接口作为父类的一个构造器，最终就是返回一个这个接口作为父类的一个对象
 */
public interface PageCache {
    public static final int PAGE_SIZE = 1 << 13;  // 这是每页的大小---这里是参照大多数数据库的设计，每页的大小默认设置为8KB(2的13次方)

    int newPage(byte[] initData);
    Page getPage(int pano) throws Exception;
    void close();
    void release(Page page);

    void truncateByPgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);

    public static PageCacheImpl create(String path, long memory){
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
        try {
            if(!f.createNewFile()){
                Panic.panic(Error.FileNotExistsException);
            }
        }catch(Exception e){
            Panic.panic(e);
        }
        if(!f.canRead() || !f.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try{
            raf = new RandomAccessFile(f,"rw");
            fc = raf.getChannel();
        }catch (Exception e){
            Panic.panic(e);
        }

        return new PageCacheImpl(raf,fc,(int) memory / PAGE_SIZE);
    }

    public static PageCacheImpl open(String path, long memory){  // 相比create方法就是这里认定这个文件是存在的
        File f = new File(path + PageCacheImpl.DB_SUFFIX);  // PageCacheImpl.DB_SUFFIX是文件后缀
        if(!f.exists()){
            Panic.panic(Error.FileNotExistsException);
        }
        if(!f.canRead() || !f.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        FileChannel fc = null;
        RandomAccessFile raf = null;
        try{
            raf = new RandomAccessFile(f,"rw");
            fc = raf.getChannel();
        }catch (Exception e){
            Panic.panic(e);
        }
        return new PageCacheImpl(raf,fc,(int) memory / PAGE_SIZE);
    }

}
