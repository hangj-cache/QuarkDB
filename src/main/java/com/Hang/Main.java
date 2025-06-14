package com.Hang;

import com.Hang.backend.common.SubArray;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    /**
     * 将数组前八位转换为长整数
     * @param buf 需要转换的字节数组
     * @return 转换后的数据
     */
    public static long parseLong(byte[] buf){
        ByteBuffer buffer = ByteBuffer.wrap(buf, 0, 8);
        return buffer.getLong();
    }
    // 测试一下
    @Test
    public void testBufferGetLong(){
        // 创建一个包含8个字节的字节数组
        //因为long 在Java中占用8个字节，每个字节占用8位，一下数组可以转换成一个long数字
        // 00000000 00000000 00000000 00000000 00000000 00000000 00001010 00000001
        // 1010 00000001 --> 2561(这不是按照一个字节一个字节算的，是直接当作一整行)
        byte[] byteArray = new byte[]{0,0,0,0,0,0,10,1};
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);  // 这是将字节数组
        long longValue = buffer.getLong();
        System.out.println("The long value is " + longValue);

    }

    // 通过案例演示一下java中的SubArray
    @Test
    public void testSubArray(){
        byte[] subArray = new byte[10];
        for(int i=0;i<subArray.length;i++){
            subArray[i] = (byte)(i+1);
        }

        // 创建两个SubArray(SubArray是自己定义的)
        SubArray sub1 = new SubArray(subArray,3,7);
        SubArray sub2 = new SubArray(subArray,3,9);

        // 修改共享数组数组
        sub1.raw[4] = (byte)44;
        System.out.println("original Array:");
        printArray(subArray);

        // 打印共享数组
        System.out.println("SubArray1: ");
        printSubArray(sub1);
        System.out.println("SubArray2: ");
        printSubArray(sub2);



    }

    private void printArray(byte[] array){
        System.out.println(Arrays.toString(array));
    }

    private void printSubArray(SubArray subArray){
        for(int i = subArray.start; i <= subArray.end; i++){
            System.out.print(subArray.raw[i] + "\t");
        }
        System.out.println();
    }
}