package com.Hang.backend.VM;

import com.Hang.backend.DM.dataItem.DataItem;

/**
 * 对底层 DataItem（数据页中真实数据）进行包装，加入事务控制字段（XMIN、XMAX）以支持 MVCC 多版本并发控制。
 *
 * Entry(记录)
 * DM 层向上层提供了数据项（Data Item）的概念，VM 通过管理所有的数据项，向上层提供了记录（Entry）的概念。
 * 上层模块通过 VM 操作数据的最小单位，就是记录。VM 则在其内部，为每个记录，维护了多个版本（Version）。
 * 每当上层模块对某个记录进行修改时，VM 就会为这个记录创建一个新的版本。
 *
 * vm向上层抽象出entry
 * entry结构：
 * [XMIN] [XMAX] [data]
 *
 * 每个entry是对每个数据项DataItem来说的，每个DataItem就对应一条记录，就是一个insert或者update这样子
 */

public class Entry {
    private static final int OF_MIN = 0;
    private static final int OF_MAX = OF_MIN + 8;
    private static final int OF_DATA = OF_MAX + 8;

    private long uid;
    private DataItem dataItem;
    private VersionManager vm;

    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        if(dataItem == null){
            return null;
        }

        Entry entry = new Entry();
        entry.dataItem = dataItem;
        entry.uid = uid;
        entry.vm = vm;
        return entry;
    }

    public static Entry loadEntry(VersionManager vm, long uid) throws Exception{
        DataItem di = ((VersionManagerImpl)vm)
    }
}
