package com.Hang.backend.DM.pageCache;

import com.Hang.backend.DM.page.Page;
import com.Hang.backend.DM.page.PageImpl;
import com.Hang.backend.common.AbstractCache;
import com.Hang.backend.utils.Panic;
import com.Hang.common.Error;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageCacheImpl extends AbstractCache<Page> implements
PageCache {

    private static final int MEM_MIN_LIM  = 10;
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;  // 原子整数变量，用于多线程环境中对页数的计数

    PageCacheImpl(RandomAccessFile file, FileChannel filechannel, int maxResource) {
        super(maxResource); // 这是调用构造器，让这个变量由父类保存下来
        if(maxResource < MEM_MIN_LIM){
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try{
            length = file.length();
        }catch(Exception e){
            Panic.panic(e);
        }

        this.file = file;
        this.fc = filechannel;
        this.pageNumbers = new AtomicInteger((int) length / PAGE_SIZE);
        this.fileLock = new ReentrantLock();
    }

    @Override
    public int newPage(byte[] initData) {
        int pgno = pageNumbers.incrementAndGet();
        // 明白了，内存中数据是以页为单位进行保存的，Page是接口，PageImpl就是页的具体实现
        Page pg = new PageImpl(pgno,initData,null);
        flush(pg); // flush就是将这个数据写到filechannel，然后持久化
        return pgno;
    }

    @Override
    public Page getPage(int pgno) throws Exception {
//        fileLock.lock();
//        long offset = pageOffset(pgno);
//        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
//        try{
//            fc.position(offset);
//            fc.read(buf);
//        }catch (Exception e){
//            Panic.panic(e);
//        }finally{
//            fileLock.unlock();
//        }
//        return new PageImpl(pgno,buf.array(),null);
        // 官方的
        return get((long)pgno);
    }

    @Override
    public void close() {
        super.close();  // 这是关闭缓存，清空所有的缓存数据
        try{
            fc.close();
            file.close();
        }catch(Exception e){
            Panic.panic(e);
        }
    }

    /**
     * 根据pageNumber从文件中读取页数据，并包裹成Page(文件的话就是从RandomAccessFile读取呗)
     * @param key
     * @return
     * @throws Exception
     */
    @Override
    protected Page getForCache(long key) throws Exception {
        // 将key转换成页码
        int pano = (int) key;
        // 计算页码对应的偏移量
        long offset = PageCacheImpl.pageOffset(pano);

        //分配一个大小为PAGE_SIZE的ByteBuffer
        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        // 锁定文件确保线程安全
        fileLock.lock();
        try{
            fc.position(offset);
            fc.read(buf);
        }catch(Exception e){
            Panic.panic(e);
        }
        fileLock.unlock();
        return new PageImpl(pano,buf.array(),this);
    }

    /**
     * 当一个Page对象（页面）不再需要在缓存中保留时，就会调用这个方法。
     * 如果这个页面被标记为"dirty"（即，这个页面的内容已经被修改，但还没有写回到磁盘），
     * 那么这个方法就会调用flush方法，将这个页面的内容写回到磁盘。
     * @param pg
     */
    @Override
    protected void releaseForCache(Page pg) {
        if(pg.isDirty()){ // 脏数据就是修改但未提交的数据嘛
            flush(pg);  // 这就是提交的操作
            pg.setDirty(false);
        }
    }

    @Override
    public void release(Page page) {  // 强行释放一个缓存（一个数据的缓存）
        release((long)page.getPageNumber());
    }

    /**
     * 将数据文件截断为只保留页号 ≤ maxPgno 的内容，并更新页号记录。
     * @param maxPgno
     */
    @Override
    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno+1);
        try{
            // 把磁盘上的文件裁剪（或扩展）成你指定的长度，确保只保留你想要的页数据。(这是真实数据，不是缓存)
            // RandomAccessFile本来就是直接作用与磁盘数据的通道
            file.setLength(size);
        }catch(Exception e){
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }


    @Override
    public void flushPage(Page pg) {
        flush(pg);
    }


    public void flush(Page pg){  // 就是提交的操作
        int pgno = pg.getPageNumber();
        long offset = pageOffset(pgno);
        fileLock.lock();
        try{
            ByteBuffer buf = ByteBuffer.wrap(pg.getData());
            fc.position(offset);
            fc.write(buf);
            fc.force(false);   // 就是写入到filechannel然后持久化
        }catch (Exception e){
            Panic.panic(e);
        }finally{
            fileLock.unlock();
        }
    }

    @Override
    public int getPageNumber(){
        return pageNumbers.get();
    }

    public static long pageOffset(int pano){
        return (pano - 1) * PAGE_SIZE;
    }
}
