package com.Hang.backend.DM.logger;

import com.Hang.backend.utils.Panic;
import com.Hang.backend.utils.Parser;
import com.Hang.common.Error;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.RandomAccess;

/**
 * 工厂模式的应用
 * 封装对象创建逻辑：将日志对象的实例化过程隐藏在接口的静态方法中，外部调用者只需知道Logger.create(path)或Logger.open(path)，无需了解具体实现类LoggerImpl。
 * 统一创建入口：所有日志对象的创建都通过这两个静态方法完成，保证创建逻辑的一致性。
 *
 * 接口内定义静态方法本质上就类似于构造器，其实最后也是去常见这个接口的一个对象（因为最后是通过多态的方式获得这个对象，接口名为对象类型名）
 * 这个方法其实就是创建这个接口对应对象，将这个实例化过程隐藏在接口中，因此所有日志对象的创建都只用调用日志接口的这个静态方法就可以了
 */

public interface Logger {
    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();

    public static Logger create(String path){
        File f = new File(path + LoggerImpl.LOG_SUFFIX);
        try{
            if(!f.createNewFile()){
                Panic.panic(Error.FileNotExistsException);
            }
        }catch (Exception e){
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

        ByteBuffer buf = ByteBuffer.wrap(Parser.int2Byte(0));  // 这就是四个字节 全零
        try{
            fc.position(0);
            fc.write(buf);
            fc.force(false);
        }catch (Exception e){
            Panic.panic(e);
        }

        return new LoggerImpl(raf,fc,0);
    }

    public static Logger open(String path){
        File f = new File(path + LoggerImpl.LOG_SUFFIX);
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

        LoggerImpl lg = new LoggerImpl(raf,fc);
        lg.init();

        return lg;
    }
}
