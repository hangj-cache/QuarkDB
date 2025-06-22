package com.Hang.backend.TM;

import com.Hang.backend.utils.Panic;
import com.Hang.backend.utils.Parser;
import com.Hang.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XID文件是用来首先记录事务的数量的，然后就是记录每一个事务所处的一个状态
 * 对 XID 文件进行校验，以保证这是一个合法的 XID 文件。
 * 校验的方式也很简单，通过文件头的 8 字节数字反推文件的理论长度，与文件的实际长度做对比。
 * 如果不同则认为 XID 文件不合法。对于校验没有通过的，会直接通过 panic 方法，强制停机。
 * 在一些基础模块中出现错误都会如此处理，无法恢复的错误只能直接停机。
 */

public class TransactionManagerImpl implements TransactionManager {

    // XID文件头长度  这个是用来表示XID文件管理事务的数量
    static final int LEN_XID_HEADER_LENGTH = 8;
    // 每个事务的占用长度  这是这个XID文件中每个事务的状态长度（其实就是这个事务的长度）
    private static final int XID_FILE_SIZE = 1;

    // 事务的三种状态
    private static final byte FIELD_TRAN_ACTIVE = 0;  // 正在进行，尚未结束
    private static final byte FIELD_TRAN_COMMITED = 1;  // 已提交
    private static final byte FIELD_TRAN_ABORTED = 2;  // 已撤销或回滚

    // 超级事务，永远为commited状态
    public static final long SUPER_XID = 0;

    static final String XID_SUFFIX = "xid";

    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;  // 这个表示xid文件管理的事务总数(就是已经开启的事务数量，每开启一个就会在xid文件中新建一个它的事务状态信息，然后总数+1)
    private Lock counterLock;

    // 构造器--没有权限修饰符表示是私有的，只能在当前包（package）中使用这个构造器创建对象。
    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();  // 只要创建了这个实现类对象，就会自动去检测这个XID文件是否合法
    }

    /**
     * 检查XID文件是否合法
     * 读取XID_FILE_HEADER中的xidCounter，根据它计算文件的理论长度，对比实际长度
     * @return
     */

    private void checkXIDCounter() {
        long fileLen = 0;
        try{
            fileLen = file.length();
        } catch (Exception e1){
            Panic.panic(Error.BadXIDFileException);
        }

        if(fileLen < LEN_XID_HEADER_LENGTH){
            Panic.panic(Error.BadXIDFileException);
        }

        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);

        try{
            fc.position(0);
            fc.read(buf);
        }catch(Exception e){
            Panic.panic(e);
        }
        // 将ByteBuffer的内容解析为长整型，作为xidCounter(+1后就是管理事务的总数量)
        this.xidCounter = Parser.parseLong(buf.array());
        // 计算xidCounter+1对应的XID位置  就是最后一个事务的位置
        long end = getXidPosition(xidCounter+1);
        if(end != fileLen){
            Panic.panic(Error.BadXIDFileException);
        }
    }

    // 根据事务xid获取这个事务在xid文件中的位置
    private long getXidPosition(long xid) {
        return LEN_XID_HEADER_LENGTH + (xid-1) *XID_FILE_SIZE;  // 因为是位置，位置从0开始
    }


    // 更新xid事务状态为status
    private void updateXID(long xid, byte status) {
        long offset = getXidPosition(xid); // 这就是这个事务在xid文件中的位置
        // 然后只需要替换掉这个长度为1的事务状态就可以了
        byte[] tmp = new byte[XID_FILE_SIZE];
        tmp[0] = status;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try{
            fc.position(offset);
            fc.write(buf);
        }catch (Exception e){
            Panic.panic(e);
        }

        try{
            fc.force(false);  // 强制写入磁盘
        }catch (Exception e){
            Panic.panic(e);  // 这是输出异常的栈信息以及错误推出程序
        }
    }

    // 将XID+1,同时更新XID Header
    private void incrXIDCounter() {
        xidCounter++; // 然后需要给xid文件中的头部进行更新
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
        try{
            fc.position(0);
            fc.write(buf);
        }catch (Exception e){
            Panic.panic(e);
        }
        // 然后在写到磁盘里
        try{
            fc.force(false); // false表示隐式写磁盘，true表示的事显式的
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // 定义一个方法，接受一个事务ID(xid)和一个状态(status)作为参数
    // 检查一个XID位置事物的状态是否和status一致
    private boolean checkXID(long xid, byte status){
        long offset = getXidPosition(xid);
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FILE_SIZE]);
//        ByteBuffer buf = ByteBuffer.allocate(XID_FILE_SIZE);  // 这样也可以
        try{
            fc.position(offset);
            fc.read(buf);
        }catch (Exception e){
            Panic.panic(e);
        }
        return buf.array()[0] == status;
    }

    // 开始一个事务，并返回XID
    @Override
    public long begin() {
        // 锁定计数器，防止并发问题  计数器就是xidCounter，每开始一个新的事务，就+1
        counterLock.lock();
        try{
            long xid = xidCounter+1;  // 因为是逐个往后面加的，因此现在的数字+1就是此刻的事务id
            // 调用updateXID方法，将新的事务ID和事务状态（这里是活动状态）写入到XID文件中
            updateXID(xid, FIELD_TRAN_ACTIVE);
            // 没开启一个新事物，就需要更新xid文件的信息，比如header数要+1
            incrXIDCounter();
            return xid;
        }finally {
            // 释放锁
            counterLock.unlock();
        }
    }

    @Override
    public void commit(long xid) {
        updateXID(xid, FIELD_TRAN_COMMITED);  // 只要是事务都会在头部数量中
    }

    @Override
    public void abort(long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    @Override
    public boolean isActive(long xid) {
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    @Override
    public boolean isCommitted(long xid) {
        return checkXID(xid, FIELD_TRAN_COMMITED);
    }

    @Override
    public boolean isAborted(long xid) {
        return checkXID(xid,FIELD_TRAN_ABORTED);
    }

    @Override
    public void close() {  // 这是用来关闭文件通道和文件的
        try {
            file.close();
            fc.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
