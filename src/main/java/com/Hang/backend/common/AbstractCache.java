package com.Hang.backend.common;

import com.Hang.common.Error;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AbstractCache 实现了一个引用计数策略的缓存
 * 引用技术缓存框架
 */
public abstract class AbstractCache<T> {
    // 键是资源的唯一标识符（通常是资源的ID或哈希值），值是缓存的资源对象（类型为 T）
    private HashMap<Long,T> cache;  // 实际缓存的数据
    private HashMap<Long, Integer> references;  // 资源的引用个数---这就是引用计数法，就是记录某个资源被引用的次数
    // 只有一个线程可以互斥获得一个资源的锁的步骤是逻辑实现的，而不是这个锁本身的性质
    private HashMap<Long, Boolean> getting;  // (这是用来保证多线程环境下的线程安全的)正在获得某资源的线程(获取资源指的是将资源加载到缓存里面，如果使缓存的话，直接拿就可以了)
    // getting 用于记录哪些资源当前正在从数据源获取中。键是资源的唯一标识符，值是一个布尔值，表示该资源是否正在被获取中。
    private int maxResource;  // 缓存的最大缓存资源数
    private int count = 0;  // 缓存中元素的个数
    private Lock lock;

    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        this.cache = new HashMap<>();
        this.references = new HashMap<>();
        this.getting = new HashMap<>();
        this.lock = new ReentrantLock();
    }

    // 从缓存中获取资源
    protected T get(long key) throws Exception{
        // 因为可能其他线程正在操作这个数据，因此要循环取获得锁----这里用死循环是因为万一其他线程在获取这个数据，那么就等会再来，所以是死循环
        while(true){
            lock.lock();  // 这个加锁是为了控制线程安全
            if(getting.get(key)){
                // 如果其他线程正在获取这个资源，自己先解锁，然后等待一毫秒然后继续循环
                lock.unlock();
                try{
                    Thread.sleep(1);
                }catch(InterruptedException e){
                    e.printStackTrace();
                    continue;
                }
                continue;
            }

            if(cache.containsKey(key)){
                // 表示当前资源没有被线程正在获得
                // 表示这个资源已经在缓存里面
                T obj = cache.get(key);  // 得到资源之后就要放开锁
                references.put(key,references.get(key)+1);
                lock.unlock();
                return obj;
            }
            // 如果资源没有在缓存中，尝试获取资源。如果缓存已满，则报错
            if(maxResource > 0 && count == maxResource){
                lock.unlock();
                throw Error.CacheFullException;
            }
            count++;
            getting.put(key,true); // 这是获取资源的过程，只有当加载到缓存中后才能拿，因此先解锁
            lock.unlock();
            break;
        }

        // 尝试获取资源
        T obj = null;
        try{
            obj = getForCache(key);
        }catch(Exception e){
            lock.lock();  // 操作这些资源的时候一定要加锁，防止报错
            count--;  // 因为前面已经默认要去获得资源，已经+1了，这里如果失败的话就-1
            getting.remove(key);
            lock.unlock();
            throw e;
        }

        // 现在已经获取到资源
        lock.lock();
        getting.put(key,false);
        cache.put(key,obj);
        references.put(key,1);
        lock.unlock();
        return obj;
    }

    /**
     * 强行释放一个缓存(指的是一个资源的缓存)(有很多个线程去引用这块资源的)
     */
    protected void release(long key){
        lock.lock();
        try{
            int ref = references.get(key) - 1;
            if(ref == 0){
                T obj = cache.get(key); // 从缓存中获取资源
                releaseForCache(obj); // 处理资源的释放（就是从缓存中把这个资源删掉）
                references.remove(key); //
                cache.remove(key);
                count--;
            }else{
                references.put(key,ref);
            }
        }finally {
            lock.unlock();
        }
    }

    /**
     * 关闭缓存，写回所有资源  就是将缓存中所有的数据都删掉
     */
    protected void close(){
        lock.lock();
        try{
            // 获取所有资源key
            Set<Long> keys = cache.keySet();
            for(long key : keys){
                // 获得这个是为了在缓存中释放这个
                T obj = cache.get(key);
                // 释放缓存(后续处理)
                releaseForCache(obj);
                // 引用计数法移除缓存
                references.remove(key);
                // 实际缓存移除缓存
                cache.remove(obj);
            }
        }finally{
            lock.unlock();
        }
    }


    /**
     * 当资源不在缓存时的获取行为---加载数据、初始化资源、预计算值（在对象首次加入缓存或回源重新加载时执行初始化）
     */
    protected abstract T getForCache(long key) throws Exception;
    /**
     * 当资源被驱逐时的写回行为---释放资源、持久化数据、清理状态
     */
    protected abstract void releaseForCache(T obj);





}
