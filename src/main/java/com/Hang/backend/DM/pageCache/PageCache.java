package com.Hang.backend.DM.pageCache;


import com.Hang.backend.DM.page.Page;
import com.Hang.backend.utils.Panic;
import com.Hang.common.Error;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * 定义页面缓存的接口，包括新建页面、获取页面、
 * 释放页面、关闭缓存、根据最大页号截取缓存、
 * 获取当前页面数量以及刷新页面等方法
 */
public interface PageCache {
    public static final int PAGE_SIZE = 1 << 13;

    int newPage(byte[] initData);
    Page getPage(int pano) throws Exception;
    void close();
    void release(Page page);

    void truncateByBgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);

    public static PageCacheImpl create(String path, long memory){
        File f = new File(path + PageCacheImpl.DB_SUFFIX);
        try {
            if(!f.createNewFile()){
                Panic.panic(Error.FileExistsException);
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

    public static PageCacheImpl open(String path, long memory){
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
