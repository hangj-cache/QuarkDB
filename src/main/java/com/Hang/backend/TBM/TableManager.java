package com.Hang.backend.TBM;

import com.Hang.backend.DM.DataManager;
import com.Hang.backend.VM.VersionManager;
import com.Hang.backend.parser.statement.*;
import com.Hang.backend.utils.Parser;

import java.io.IOException;

/**
 * 表管理器
 * 负责管理数据表的创建、查询、更新、删除、事务控制等核心功能。
 *
 * TableManager 是数据库中“表级别操作”的抽象接口，定义了所有对数据表的操作行为和事务控制方法，供系统其他模块调用。
 */
public interface TableManager {
    BeginRes begin(Begin begin);  // 开启一个新事务，返回事务 ID（xid） 和其它初始化信息
    byte[] commit(long xid) throws Exception; // 提交指定事务
    byte[] abort(long xid); // 回滚（撤销）指定事务

    byte[] show(long xid);  // 显示当前系统中有哪些表（元数据查询）
    byte[] create(long xid, Create create) throws Exception;  // 在指定事务中创建一张新表（建表）

    byte[] insert(long xid, Insert insert) throws Exception;  // 向表中插入记录
    byte[] read(long xid, Select select) throws Exception;  // 从表中读取记录
    byte[] update(long xid, Update update) throws Exception;  // 更新表中某些记录
    byte[] delete(long xid, Delete delete) throws Exception;  // 删除表，这里是表管理

    // 工厂方法（用于构造 TableManager 实例）
    public static TableManager create(String path, VersionManager vm, DataManager dm){
        Booter booter = Booter.create(path);  // 创建一个新的表管理器实例，负责初始化 booter 文件（写入初始状态，比如 0）
        booter.update(Parser.long2Byte(0));
        return new TableManagerImpl(vm, dm, booter);
    }

    public static TableManager open(String path, VersionManager vm, DataManager dm){
        Booter booter = Booter.open(path);  // 打开已有数据库目录中的 booter 文件，加载已有状态。
        return new TableManagerImpl(vm,dm,booter);
    }

}
