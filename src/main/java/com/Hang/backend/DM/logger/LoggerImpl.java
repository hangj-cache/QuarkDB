package com.Hang.backend.DM.logger;

import com.Hang.backend.utils.Panic;
import com.Hang.backend.utils.Parser;
import com.Hang.common.Error;
import com.google.common.primitives.Bytes;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志文件读写
 *
 * 日志文件标准格式为:
 * [XChecksum] [Log1] [Log2] [] ... [LogN] [BadTail]
 * XChectsum 为后续所有日志计算的Checksum, int类型
 *
 * 每条正确日志的格式为：
 * [Size] [Checksum] [Data]
 * Size 4字节 int标识Data长度
 * Checksum 4字节 int
 */
public class LoggerImpl implements Logger{

    private static final int SEED = 13331; // 这是自己任意取的一个常数，用于计算日志文件的校验和

    // 一个正确日志的组成就是  size + checksum + data三部分 OF表示这些对应字段的偏移量---这些只是针对单个日志的相对数据偏移量
    private static final int OF_SIZE = 0; // 这表示size字段的偏移量
    private static final int OF_CHECKSUM = OF_SIZE + 4;  // 这表示校验和字段的偏移量
    private static final int OF_DATA = OF_CHECKSUM + 4;  // 这表示data数据字段的偏移量

    public static final String LOG_SUFFIX = ".log";  // suffix表示后缀

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    private long position;  // 当前日志指针的位置
    private long fileSize;  // 初始化时记录，log操作不更新
    private int xChecksum;

    LoggerImpl(RandomAccessFile raf, FileChannel fc){
        this.file = raf;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    LoggerImpl(RandomAccessFile raf, FileChannel fc, int xChecksum){
        this.file = raf;
        this.fc = fc;
        this.xChecksum = xChecksum;
        lock = new ReentrantLock();
    }

    void init(){
        long size = 0;
        try{
            size = file.length();
        } catch (Exception e){
            Panic.panic(e);
        }

        if(size < 4){
            Panic.panic(Error.BadLogFileException);
        }

        ByteBuffer raw = ByteBuffer.allocate(4);
        try{
            fc.position(0);
            fc.read(raw);
        }catch (Exception e){
            Panic.panic(e);
        }

        int xChecksum = Parser.parseInt(raw.array());
        this.fileSize = size;
        this.xChecksum = xChecksum;

        checkAndRemoveTail();
    }

    // 检查并移除bad tail----打开一个日志，肯定要先检查校验和，然后移除坏尾（坏尾就是没来得几写完的日志数据）
    private void checkAndRemoveTail(){
        rewind();

        int xCheck = 0;
        while(true){
            byte[] log = internNext();
            if(log == null){  // 这说明已经有问题了，不用继续计算校验和进行检查了
                break;
            }
            xCheck = calChecksum(xCheck, log);
        }
        if(xCheck != xChecksum){
            Panic.panic(Error.BadLogFileException);
        }

        try{
            truncate(position);  // 这是截断损毁的部分 position作为全部变量在internNext调用的时候就在不断变化，最终会指向最后一个坏尾的开头，这里直接将它进行截断
        }catch (Exception e){
            Panic.panic(e);
        }

        try{
            file.seek(position); // 确保指针位于新的文件末尾，这是filechannel的指针，和position不是一个东西，这是将这个指针移到position的位置
            // 将文件指针设置为从文件开头算起的指定字节位置
            // RandomAccessFile和从RandomAccessFile得到的fileChannel的指针是共享的，没有延迟
        }catch (Exception e){
            Panic.panic(e);
        }

        rewind(); // 最终初始化文件头，就是position
    }

    private byte[] internNext(){  // 这个的作用是读取当前的日志，同时移动position指向下一个日志的开头的位置
        if(position + OF_DATA >= fileSize){
            return null;
        }
        ByteBuffer tmp = ByteBuffer.allocate(4);
        try{
            fc.position(position);
            fc.read(tmp);
        }catch (Exception e){
            Panic.panic(e);
        }

        int size = Parser.parseInt(tmp.array());  // 这是从字节数组转整数的方法====明明可以直接int size = tmp.getInt();的
        if(position + size + OF_SIZE > fileSize){
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size); // OF_DATA指的是data前面的size + checksum的偏移
        try{
            fc.position(position);
            fc.read(buf); // 这读取的就是整个日志的数据了
        }catch (Exception e){
            Panic.panic(e);
        }

        byte[] log = buf.array(); // 将这个日志的数据转成字节数组
        int checksum1 = calChecksum(0, Arrays.copyOfRange(log,OF_DATA,log.length)); // 这是计算引入的这个log的校验和，同时记录为checksum1
        int checksum2 = Parser.parseInt(Arrays.copyOfRange(log,OF_CHECKSUM,OF_DATA));  // 这是将引入的log日志的校验和提取出来
        if(checksum1 != checksum2){
            return null;  // 这里是检验传入的这个日志的校验和是否正确。如果不正确就返回null
        }
        position += log.length;  // 这里更新position到下一条日志开头的位置
        return log; // 返回当前读取出来的日志，然后position指向下一条日志
    }

    /*
    字节数组：log = [104, 101, 108, 108, 111]，字节数组就是每个元素都是一个字节，而一个字节就是八位，因此一个字节代表的整数就是0-255的一个整数
     */
    // 计算单个日志的校验和字段
    private int calChecksum(int xCheck, byte[] log){
        // Checksum是单条日志的校验和, 这是对log中每一个字节进行计算累加，然后基于此生成这个日志的校验和
        for(byte b : log){
            xCheck = xCheck * SEED + b;
        }
        return xCheck;
    }

    @Override
    public void log(byte[] data) {
        byte[] log = wrapLog(data);
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();
        try{
            fc.position(fc.size());
            fc.read(buf);
        }catch (Exception e){
            Panic.panic(e);
        }finally{
            lock.unlock();
        }
        updateXChecksum(log);
    }

    private void updateXChecksum(byte[] log){  // 这是加入一个新的日志，然后更新整个日志文件的校验和
        this.xChecksum = calChecksum(this.xChecksum, log);
        try{
            fc.position(0);
            fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
            fc.force(false); // 这是进行刷盘
        }catch (Exception e){
            Panic.panic(e);
        }
    }

    // wrapLog 是用来组合size、checksum以及data字段，最终返回的就是一个完整的日志数据（字节数组）
    private byte[] wrapLog(byte[] data){
        byte[] checksum = Parser.int2Byte(calChecksum(0, data));  // 这是计算日志文件的校验和
        byte[] size = Parser.int2Byte(data.length); // 数据长度字段
        return Bytes.concat(size, checksum, data);  // 这就是单个日志的结构
    }

    @Override
    public void truncate(long x) throws Exception {  // truncate 截断的意思
        lock.lock();
        try{
            fc.truncate(x);  // 如果长度小于x就扩展到x，多的空间补0，如果长度大于x就截断到x
        }finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] next() {
        lock.lock();
        try{
            byte[] log = internNext();
            if(log == null){
                return null;
            }
            return Arrays.copyOfRange(log,OF_DATA,log.length);  // 这里是只返回日志数据
        }finally {
            lock.unlock();
        }
    }

    @Override
    public void rewind() {  // rewind表示倒带，就是回复position的最初位置
        position = 4;  // 整个日志文件的开头是有4个字节的Xchecksum的，因此要偏移四个字节，后面就都是日志数据了
    }

    @Override
    public void close() {
        try{
            /*
            先关闭上层的filechannel，再关闭底层的RandomAccessFile，才是安全的,fileChannel是通过RandomAccessFile.getChannel获得的
             */
            fc.close();
            file.close();
        }catch (Exception e){
            Panic.panic(e);
        }
    }
}
