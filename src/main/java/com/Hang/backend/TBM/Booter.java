package com.Hang.backend.TBM;

import com.Hang.backend.utils.Panic;
import com.Hang.common.Error;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 写入临时文件	避免直接破坏原文件
 * Booter 字面意思是“启动器”，在数据库/存储引擎中，booter 文件通常是元数据文件，用于保存系统启动时必须读取的重要状态信息。
 * 它相当于数据库或者存储系统的“启动入口配置”或“状态快照”。
 *
 * Booter 类负责安全地读取和更新数据库系统的元数据启动文件（booter 文件），并通过 .tmp 临时文件 + 原子替换机制，确保文件写入的原子性和可靠性。
 */
// 记录第一个表的uid

public class Booter {
    public static final String BOOTER_SUFFIX = ".bt";  // 正式的 booter 文件
    public static final String BOOTER_TMP_SUFFIX = ".bt_tmp";  // 临时写入用的临时文件

    String path;  // 数据目录前缀路径（不包含后缀）
    File file;  // 当前操作的正式 booter 文件对象

    // 用于创建一个新的 booter 文件（比如在初始化数据库时）
    public static Booter create(String path) {
        removeBadTmp(path);  // 删除遗留的 .bt_tmp 临时文件（防止脏状态）
        File f = new File(path + BOOTER_SUFFIX);
        try{
            if(!f.createNewFile()){
                Panic.panic(Error.FileExistsException);
            }
        }catch(IOException e){
            Panic.panic(e);
        }
        if(!f.canRead() || !f.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        return new Booter(path,f);
    }

    // 用于打开一个已有的 booter 文件（比如在数据库重启或恢复时）
    public static Booter open(String path) {
        removeBadTmp(path);
        File f = new File(path + BOOTER_SUFFIX);
        if(!f.exists()){
            Panic.panic(Error.FileNotExistsException);
        }
        if(!f.canRead() || !f.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        return new Booter(path,f);
    }

    // 删除指定路径下的临时文件（以 .bt_tmp 结尾的临时标记文件）。
    private static void removeBadTmp(String path) {
        new File(path+BOOTER_TMP_SUFFIX).delete();
    }

    private Booter(String path, File file) {
        this.path = path;
        this.file = file;
    }

    // 从磁盘上读取 booter 文件的全部内容（一次性读入为 byte[]）
    public byte[] load(){
        byte[] buf = null;
        try{
            // 使用 Java NIO 的 Files.readAllBytes(...) 方法一次性读取整个文件的所有内容。
            buf = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            Panic.panic(e);
        }
        return buf;
    }

    // 安全地将一段字节数据写入指定文件（booter 文件），通过先写临时文件再原子覆盖实现写入过程的“原子性”和“异常恢复”能力。
    // 安全地将数据写入 booter 文件，采用 写临时文件 → 原子替换 的方式：
    // 这里所说的原子操作就是直接通过重命名+替换的方式实现的
    // 通过“重命名（rename）”或“移动（move）”临时文件来替换正式文件，整个过程是一步完成的，不可被中断。
    public void update(byte[] data){
        File tmp = new File(path + BOOTER_TMP_SUFFIX); // 构造 .tmp 临时文件路径（例如：meta.booster.tmp）。
        // new File这只是虚拟的一个文件路径，可能存在可能不存在的
        try{
            tmp.createNewFile();  // 尝试创建该文件（如果之前没创建过）。
        }catch (IOException e){
            Panic.panic(e);
        }

        if(!tmp.canRead() || !tmp.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        //  try-with-resources 写法，和普通的 try-catch-finally 不太一样
        /*
        out 会在 try 代码块执行结束后自动调用 close()，不需要手动写 finally。
        FileOutputStream 实现了 AutoCloseable 接口，所以可以使用这种写法。
        如果 close() 出错，也能自动抛出或被捕获（如果你写了 catch）。
        就是多了一个自动close的功能，按照原来的写法FileOutputStream out = new FileOutputStream(tmp)也是写在try{}中
         */
        try(FileOutputStream out = new FileOutputStream(tmp)){
            out.write(data);  // 使用 FileOutputStream 将 data 写入 .tmp 文件。
            out.flush();  // 使用 flush() 保证数据写入到磁盘缓存。
        }catch (IOException e){
            Panic.panic(e);
        }
        try{
            // 使用 Files.move(...) 方法将 .tmp 文件 原子性地替换正式文件（如 .booter）。
            // StandardCopyOption.REPLACE_EXISTING：替换旧文件，防止报错。
            // 这个步骤确保了：要么原文件完整，要么新文件完整，中间不会出现“半写”的损坏状态。
            // 把 .tmp 文件原子性地移动（或重命名）为 .booter 文件，如果 .booter 已存在，则覆盖它。
            // 删除原来的 .booter 文件（如果存在）
            // 把 .tmp 文件重命名为 .booter
            //一切作为一个 原子操作 执行（操作系统层面）-----写到tmp文件和booter文件速度都是一样的，只是为了安全
            // 因为直接写booter文件会导致booter文件损坏
            Files.move(tmp.toPath(), new File(path+BOOTER_SUFFIX).toPath(), StandardCopyOption.REPLACE_EXISTING);
            // Files.move(sourcePath, targetPath, options)
            // sourcePath: 源文件路径（你要移动的文件，这里是 .tmp）
            // targetPath: 目标路径（你要替换的正式文件 .booter）
            // StandardCopyOption.REPLACE_EXISTING: 如果目标文件已存在，就覆盖它
            /*
            为什么要这么做？
            因为直接写入 .booter 文件可能发生如下问题：
            程序中途崩溃、电源突然断电、I/O 异常中断、如果你直接写 .booter，会导致：文件内容不完整、元数据损坏、系统启动失败或恢复失败
            所以更安全的做法是：先写 .tmp 文件，写完后，用 Files.move(...) 替换原文件
            这样就能保证文件更新是事务性的，要么成功、要么失败，不会有“半成功”。
             */
        }catch (IOException e){
            Panic.panic(e);
        }
        // 更新 file 成为新写入的文件对象。（就是有tmp文件替换之后的booter文件）
        file = new File(path+BOOTER_SUFFIX);
        if(!file.canRead() || !file.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
    }
}
