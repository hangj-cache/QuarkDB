package com.Hang.backend.DM.page;

import com.Hang.backend.DM.pageCache.PageCache;
import com.Hang.backend.utils.RandomUtil;

import java.util.Arrays;

/**
 * 特殊管理的一页
 * ValidCheck
 * db启动时给100~107字节处填入一个随机字节，db关闭时将其拷贝到108~115字节
 * 用于判断上一次数据库是否正常关闭
 */
public class PageOne {
    private static final int OF_VC = 100;  // 这是版本控制前面的100个字节的偏移量
    private static final int LEN_VC = 8;  // 版本控制的长度

    public static byte[] InitRaw(){
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setVcOpen(raw);  // 启动时设置初始字节
        return raw;
    }

    public static void setVcOpen(Page pg){
        pg.setDirty(true);
        setVcOpen(pg.getData());
    }

    private static void setVcOpen(byte[] raw){  // 方法重载
        System.arraycopy(RandomUtil.randomBytes(LEN_VC), 0, raw, OF_VC, LEN_VC);
    }

    public static void setVcClose(Page pg){
        pg.setDirty(true);
        setVcClose(pg.getData());
    }

    private static void setVcClose(byte[] raw){
        System.arraycopy(raw, OF_VC, raw, OF_VC+LEN_VC, LEN_VC);
    }

    /*
    校验字节
     */
    public static boolean checkVc(Page pg){
        return checkVc(pg.getData());
    }

    private static boolean checkVc(byte[] raw){
        // 就是对比100 ~ 107 字节和108 ~ 115 字节是否相等来判断上次是否是正常关闭
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, OF_VC+LEN_VC),Arrays.copyOfRange(raw, OF_VC+LEN_VC, OF_VC+2*LEN_VC));
    }


}
