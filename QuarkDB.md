# MYDB

Maven在Java项目，不管是springboot项目都是很流行的，用来管理依赖！！！

### 主要组件:

1. Transaction(事务) Manager（TM）
2. Data Manager（DM）---作用：1、作为上层模块和文件系统之间的一个抽象层，向下直接读写文件（这就是提供缓存的功能，就是将数据先从文件中读取出来供上层使用，或者是上层将数据先放到缓存中，然后一次性写入到磁盘上，避免频繁请求），向上提供数据的包装，另外就是日志的功能
3. Version Manager（VM）
4. Index Manager（IM）
5. Table Manager（TBM）

### 实现顺序: TM -> DM -> VM -> IM -> TBM



记住所谓缓存，其实就是除了文件存储之外的一种存储方式，程序将数据从文件中读取出来，其实就是读取到缓存中了。

在缓存系统中，“**回源**”有时也叫做“**回写（Write Back）**”或“**写穿（Write Through）**”的一部分策略，它通常指的是：

- 当缓存中的数据发生变更或者被淘汰（例如 LRU 策略淘汰了最久未使用的数据），
- 考虑是否需要把这个数据更新/保存到后端的数据源中，以确保**数据一致性**或避免数据丢失。

LRU缓存存在一个尴尬的地方，这里首先从缓存的接口设计说起，如果使用 LRU 缓存，那么只需要设计一个 `get(key)` 接口即可，释放缓存可以在缓存满了之后自动完成。设想这样一个场景：某个时刻缓存满了，缓存驱逐了一个资源，这时上层模块想要将某个资源强制刷回数据源，这个资源恰好是刚刚被驱逐的资源。那么上层模块就发现，这个数据在缓存里消失了，这时候就陷入了一种尴尬的境地：是否有必要做回源操作？



LRU就是在缓存满的时候自动释放资源，而现在采用的引用计数框架也是存在缓存满的问题，当采用引用计数框架，缓存满的时候，由于引用技术框架无法自动释放缓存，此时直接报错（和JVM类似，直接OOM）。

用引用计数法的话就是手动驱逐缓存，使驱逐缓存可控。



## TM

1. 对于项目中**backend**是后端的意思，那么这些组件就都放在这个backend包下，然后其他的比如使用者可以放在client包下面，然后中间的传输可以放在transport包下面

2. **Panic** 通常指的是一个严重的错误或异常情况，因此可以用这个定义异常类

3. 构造器就是为一个类初始化实例变量同时执行一些初始化方法（因此，构造器里面是可以放方法执行的）

4. `RandomAccessFile` 是一个用于读写文件的类，它允许程序以随机访问的方式操作文件。这意味着你可以直接跳转到文件的任意位置进行读写操作，而不需要从文件的开头顺序读取或写入。

5. `FileChannel` 的主要特点：也提供了 `position(long newPosition)` 方法，也可以设置文件指针的位置，也可以读取任意位置的内容

   1. **高效读写**：

      - `FileChannel` 提供了高性能的文件读写操作，特别是在处理大文件时，比传统的 `RandomAccessFile` 更高效。

      - 支持直接缓冲区（Direct Buffers），可以减少数据在用户空间和内核空间之间的拷贝，提高性能。

   2. **内存映射文件**：

      - `FileChannel` 支持内存映射文件（Memory-Mapped Files），允许将文件的一部分或全部映射到内存中，使得文件操作就像操作内存一样高效。
      - 内存映射文件可以显著提高文件读写的速度，尤其是在随机访问大文件时。

   3. **文件锁定**：

      - `FileChannel` 提供了文件锁定机制，可以用于防止多个进程或线程同时写入同一个文件区域，确保数据的一致性。

   4. **文件位置操作**：

      - `FileChannel` 提供了方法来获取和设置文件指针的位置，类似于 `RandomAccessFile` 的 `seek` 方法。

   ==这两个侧重点不同：

   使用场景

   **`RandomAccessFile` 的适用场景**

   - **简单文件操作**：当你需要进行简单的文件读写操作，且不需要高性能时，`RandomAccessFile` 是一个很好的选择。
   - **直接读写基本数据类型**：`RandomAccessFile` 提供了丰富的读写方法，可以直接读写基本数据类型，适合需要直接操作这些数据的情况。

   **`FileChannel` 的适用场景**

   - **高性能文件操作**：当你需要高效地==处理大文件==时，`FileChannel` 提供了更好的性能。
   - （文件读写都采用了==NIO 方式的 FileChannel==，读写方式都和传统 IO 的 Input/Output Stream 都有一些区别）
   - **内存映射文件**：`FileChannel` 支持内存映射文件，可以显著提高文件操作的性能，尤其是在随机访问大文件时。
   - **文件锁定**：当你需要防止多个进程或线程同时写入同一个文件区域时，`FileChannel` 提供了文件锁定机制。

   ```
    是的：FileChannel 和 RandomAccessFile 实际上都不是直接写磁盘，而是通过操作系统的页缓存（Page Cache）间接读写磁盘文件。
   所以：它们的写入不是立刻落盘的，是缓存在内核页缓存中的。
   一般操作文件都会通过getchannel来获得通道，然后操作数据的，这样效率更高
   RandomAccessFile raf = new RandomAccessFile("demo.txt", "rw");
   FileChannel channel = raf.getChannel();
   ByteBuffer buffer = ByteBuffer.wrap("Hello".getBytes());
   channel.write(buffer); // ✅ 数据写入到内核缓冲区，但还未写入磁盘
   
   channel.force(true);   // ✅ 立即要求 OS 刷新缓冲到磁盘，确保数据安全
   ```

   **3. 配合使用**

   在某些情况下，`RandomAccessFile` 和 `FileChannel` 可以配合使用，以结合两者的优点。`RandomAccessFile` 提供了一个 `getChannel` 方法，可以获取与 `RandomAccessFile` 关联的 `FileChannel`。这样，你可以同时利用 `RandomAccessFile` 的简单易用性和 `FileChannel` 的高性能特性。==总之就是大文件操作用channelfile，对于简单的文件读写操作直接用randomAccessFile就可以==

   

   ==randomAccessFile其实是new File(就是File类型的包装类)比如有File f，然后raf = new RandomAccessFile(f,"rw")，然后又可以通过getchannel获得filechannel==

   ```
                           ┌──────────────────────┐
                           │ RandomAccessFile     │
                           └─────────┬────────────┘
                                     │ getChannel()
                                     ▼
                               ┌─────────────┐
                               │ FileChannel │
                               └─────────────┘
                                     ▲
                                     │ getChannel()   
                           ┌─────────┴─────────────┐
                           │ FileInput/OutputStream│
                           └───────────────────────┘
   总之，FileChannel 可以基于 RandomAccessFile 创建，但它并不是“完全依赖”或“必须基于” RandomAccessFile。
   FileChannel 是 Java NIO 提供的用于高性能文件读写的通道类，它可以通过多个类获得，包括但不限于 RandomAccessFile。
   NIO 是非阻塞式的，配合 Selector 可以用 一个线程处理成千上万个连接。
   
   即使你只是同时操作多个文件、并通过 getChannel() 获取 FileChannel，你已经在使用 Java NIO 的机制了。
   虽然没有用到网络非阻塞模型或 Selector，但 FileChannel + ByteBuffer 本身就体现了 NIO 在文件操作领域的高性能设计。
   ```

   | 特性         | FileInputStream      | RandomAccessFile                 |
   | :----------- | :------------------- | :------------------------------- |
   | 访问方式     | 顺序读取             | 随机访问                         |
   | 读写模式     | 只读                 | 可读写                           |
   | 文件指针操作 | 不支持               | 支持(seek等)                     |
   | 数据类型读取 | 仅字节流             | 支持多种数据类型                 |
   | 性能         | 较高(适合顺序读取)   | 较低(随机访问开销大)             |
   | 典型用途     | 简单文件读取、流处理 | 数据库、索引文件、大文件部分访问 |

6. `buf.array()` 是 `ByteBuffer` 类的一个方法，用于获取底层的字节数组。`ByteBuffer` 是 Java NIO（New Input/Output）库中的一个类，用于表示一个字节缓冲区，它可以用于高效的 I/O 操作。

7. **`ByteBuffer buf = ByteBuffer.wrap(tmp);`**

   - 使用 `ByteBuffer.wrap` 方法将字节数组 `tmp` 包装成一个 `ByteBuffer` 对象。这个 `ByteBuffer` 对象可以直接用于文件或网络操作。

8. **接口中可以提供抽象方法（只声明方法，不写实现，abstract省略），默认方法（default），静态方法（public static：有static就可以），私有方法(private，java9才支持)==(所以是不限定于public还是private)==**

9. **`Panic`**在程序运行中遇到严重异常时，打印错误信息并立刻终止程序执行。

```java
public class Panic {
    public static void panic(Exception err){
        err.printStackTrace();    // 1. 打印异常堆栈信息到控制台
        System.exit(1);           // 2. 强制退出程序，状态码为1（表示错误退出）
    }
}
```

10. 一个项目中一般会定义一个异常类Error（放在common包中），这是为了集中放在一个类中，方便团队协作和后期维护。如果某个错误提示要改，不用到处找。

```
public static final Exception CacheFullException = new RuntimeException("Cache is full!");
new RuntimeException("Cache is full!")	创建一个新的运行时异常对象，并附上错误信息
虽然都是 RuntimeException，但你通过定义名字明确告诉别人 —— 这个异常到底是什么场景下抛出的。提高可读性和可维护性
```

11. **`Parser` 类通常就是“解析类”**，用来将某种输入（如文本、代码、配置、数据流等）**转换成程序可以理解的数据结构**。

12. **`Tokenizer` 是负责把原始文本切成“一个个小单元”的类**，这些小单元就叫 “token”。

13. `common` 包（或模块）一般是用来放置**“通用、可复用、无业务依赖的公共代码”**。

14. `Exception` 是不需要你手动 `import` 的，这是因为其属于java.lang包中的类，所有 `java.lang` 包中的类，**默认自动导入**，不需要显式写 `import`，不想IOException，这是需要导包的(这在java.io包里面，因此需要导包)

15. ```
    ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
    创建一个容量为 LEN_XID_HEADER_LENGTH 字节的字节缓冲区（ByteBuffer），并将其赋值给变量 buf。
    这就是一块缓冲区，用来实现高性能IO的，放进去什么类型的数据，输出也是一样的
    ByteBuffer 虽然底层是字节数组，但它可以存放多种基本数据类型（如 int, long, float, double, char, short 等），因为它提供了专门的 put/get 方法来处理这些类型的数据。
    ```

16. ```
    ByteBuffer buffer = ByteBuffer.wrap(buf, 0, 8);
    return buffer.getLong();
    
    ByteBuffer.wrap(buf, 0, 8)
    wrap(byte[] array, int offset, int length) 是 ByteBuffer 的静态方法：
    
    它会用现有的字节数组 buf 的一部分创建一个 ByteBuffer，而不是重新分配内存。
    
    这里表示：从 buf 的第 0 个字节开始，使用 8 个字节的数据。
    
    buffer.getLong()
    读取 8 个字节，并将它们解析成一个 long 类型（64 位）。
    ```

17. ```
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>   <!-- JUnit 5 -->
        <version>5.9.3</version>
        <scope>test</scope>                      <!-- 只在测试中生效 -->
    </dependency>
    JUnit 是 Java 中最常用的 单元测试框架，而你提到的 “JUnit 依赖”，就是为了在你的项目中使用 JUnit 提供的测试功能。就是@Test注解就是这个依赖下面的（添加这个依赖才可以编写测试类）
    ```

18. ```
    用现有的字节数组 buf 的一部分创建一个 ByteBuffer，其实就是将已有的原始字节数组包装为 ByteBuffer，可以让你用更高级、更方便、更安全的方式去读取和写入各种数据类型（如 int、long、char、float 等）。直接用byte[]需要进行手动拼接，而bytebuffer提供更加好用的方法来操作。
    getInt()、getLong()、getChar() 直接用
    ```

19. ```
    fc.position(0); // 这是定位
    fc.read(buf); // 这个read是将内容督导buf
    fc.write(buf); // 这个write是将buf里的内容写到fc的定位的位置上
    ```

20. ```
    try{
        fc.force(false);
    }catch (Exception e){
        Panic.panic(e);
    }
    强制将文件通道（fc）中的内容刷写到磁盘，如果失败，就调用自定义的 Panic.panic(e) 方法强制终止程序。
    ```

21. ```
    程序的退出本质是通过System.exit()，而不是通过异常（`0`：表示正常退出（成功）非 0：表示异常退出（失败）)
    ```

22. ```
    如果一个类的构造器没有任何修饰符，这意味着它是：🔒 包私有（默认访问权限）
    也就是说：只能在当前包（package）中使用这个构造器创建对象。
    四种访问权限
    | 修饰符          | 访问范围     | 构造器效果                  |
    | ------------ | -------- | ---------------------- |
    | `public`     | 所有类都能访问  | 构造器可被任何地方调用            |
    | `protected`  | 当前包 + 子类 | 多用于继承构造器控制             |
    | **（默认）无修饰符** | 当前包      | 只能在同一个包中使用             |
    | `private`    | 当前类内部    | 单例模式、工厂模式常用，禁止外部直接 new |
    
    ```

23. ```
    字节数组中的每个字节，其实就是一个 0~255 范围内的整数，通常我们会将它理解为一个 无符号的 8 位十进制数。
    就是说每个字节其实就是0~255范围内的整数
    ```

24. ```
    try {
        fc.position(offset); // 设置文件读取的位置
        fc.read(buf);        // 从文件中读取数据到 ByteBuffer
    } catch (Exception e) {
        Panic.panic(e);      // 如果出错，打印异常并终止程序
    }
    因为下面这两行代码都可能抛出异常：
    fc.position(offset)：
    如果 offset 无效，比如超出文件长度，可能抛出 IOException。
    fc.read(buf)：
    如果文件关闭了、磁盘出错、权限不足、读取失败等，都会抛出 IOException。
    因此需要try   catch
    ```

25. ```
    由bytebuffer转为字节数组====用.array()[0]就可以   .array()获得的是数组，[0]取到的是值
    ```

    ==wrap:包裹的意思，ByteBuffer.wrap(buf,0,2)**将一个字节数组包装成 ByteBuffer 对象**，不复制数据。==

26. ```
    基本类型和字节数组之间的相互转换---用bytebuffer来实现就可以  bytebuffer的空间是以字节为单位的
    比如short--> byte[] (short2Byte)  
    ByteBuffer.allocate(Short.SIZE / byte.SIZE).putShort(value).array();
    
    byte[] buf--> short (parseShort)
    ByteBuffer.wrap(buf,0,2).getShort()   这是因为short本身就只占2个字节，因此分配两个字节的内容是最好的
    ```

27. ```
    字节数组转字符串
    [字符串长度（4字节）][字符串的字节数据（length字节）]（这是定义好的规则，前四个字节表示长度）
    这是编码规定的，输入的字节数组前四个字节就是长度信息
    // 从一个字节数组 raw 中解析出一个字符串，并返回这个字符串及其总占用的字节长度。
    public static ParseStringRes parseString(byte[] raw) {  
            int length = parseInt(Arrays.copyOf(raw, 4));  //从前 4 个字节中解析出字符串的长度（假设是 length = 5）
            String str = new String(Arrays.copyOfRange(raw, 4, 4+length));  
            return new ParseStringRes(str, length+4);
        }
        
    使用 new String(byte[]) 就可以将字节数组转换为字符串。
    ```

28. ```
    字符串转字节数组---也是长度字节数组+内容字节数组
    public static byte[] string2Byte(String str) {
            byte[] l = int2Byte(str.length());
            return Bytes.concat(l, str.getBytes());
        }
    ```

29. ```
    获取字符串的字节数组和获取int类型的数的字节数组方式是不一样的
    字符串直接
    String str = "Hello";
    byte[] strBytes = str.getBytes("UTF-8"); // 常用编码方式
    
    整数需要借助先放到ByteBuffer，然后array()方法
    ```

    

## DM

1. ```
   ReadWriteLock lock = new ReentrantReadWriteLock();
   rLock = lock.readLock();
   wLock = lock.writeLock();
    Java 并发包中读写锁（ReadWriteLock）机制的典型使用方式，用于控制并发访问共享资源时的读写同步。
    
   🟦 rLock = lock.readLock();
   获取这个读写锁中的“读锁”部分。
   
   多个线程可以同时持有读锁，只要没有任何线程持有写锁。
   
   适合读取共享数据时使用，效率高于互斥锁。
   
   🟥 wLock = lock.writeLock();
   获取这个读写锁中的“写锁”部分。
   
   写锁是独占的：如果有线程持有写锁，其它线程无论是读还是写都会阻塞。
   
   适合写入/更新共享数据时使用。
   ```

2. ```
   public static final int PAGE_SIZE = 1 << 13;表示页面大小
   public static final：定义一个 公共的静态常量，在类中可全局访问，值不可修改。
   
   int PAGE_SIZE：变量名是 PAGE_SIZE，类型是 int。
   
   1 << 13：位运算，表示 将数字 1 左移 13 位。
   ```

3. ```
   | 修饰符              | 同类中 | 同包中 | 子类中（不同包） | 其他包中 |
   | ----------------   | ---   | ---   | -------- | ---- |
   | `public`           | ✅   | ✅   | ✅        | ✅    |
   | `protected`        | ✅   | ✅   | ✅        | ❌    |
   | `无修饰（default）`  | ✅   | ✅   | ❌        | ❌    |
   | `private`          | ✅   | ❌   | ❌        | ❌    |
   ```

4. ```
   Thread 可以识别并操作“当前正在执行的线程”
   🔹 原因：
   Java 中的 Thread.sleep() 和很多 Thread 的静态方法，都是作用于当前正在执行那段代码的线程。而Java 虚拟机（JVM）始终知道哪个线程正在运行当前这段代码。
   ```

5. ```
   throw Error.CacheFullException;
   当前线程会被中断
   控制台会输出异常信息和堆栈（stack trace）
   如果是在 main 方法中，程序就会终止
   如果是在子线程中，只有该子线程结束，不会影响主线程
   ```

6. ```
   抛出异常（比如 throw new CacheFullException()）和 try-catch 到底有什么关系？
    一、throw 是抛出异常，try-catch 是处理异常
    throw new CacheFullException()这行一执行，当前线程的执行流程就立即中断，并抛出一个异常对象。
    
   try-catch：表示我预料到这里可能抛异常，我来处理它，不让程序崩溃。
   try {
       // 有可能出错的代码
   } catch (CacheFullException e) {
       // 出错后我来处理
   }
   
   执行流程是这样的：
   线程执行到 throw new CacheFullException();
   
   JVM 查找有没有对应的 try-catch 能捕获这个异常（类型匹配）
   
   如果有，就跳进 catch 块，程序继续执行
   
   如果没有，继续向上层调用者查找（调用栈向上）
   
   最后仍没找到，就：
   
   终止当前线程
   
   控制台打印异常堆栈
   (因为 没有try catch，抛出的异常就没人处理，线程就死了。)
   ```

   ==不一定要用try catch，也可以用try finally (一般这种就finally后面就时对锁进行解锁，lock.unlock()，这就保证不管有没有异常，都会释放锁资源)==

   | 场景                                           | 用法推荐            | 原因                   |
   | ---------------------------------------------- | ------------------- | ---------------------- |
   | 你**期望捕获并处理异常**（恢复、提示、降级等） | `try-catch`         | 你能“挽救”异常         |
   | 你**无法/不想处理异常，只要确保释放资源**      | `try-finally`       | 只负责清理，不负责处理 |
   | 你**既要处理异常，又要释放资源**               | `try-catch-finally` | 两者都做               |

   你能挽救异常，就 catch；不能挽救，就 throw；
   能提供信息，就打日志；能封装，就转业务异常；
   所有资源，都要 finally 清理。

7. ```
   那为什么又引入了System.exit(1)，这里抛出异常不就能使线程终止吗？public class Panic {
       public static void panic(Exception err){
           err.printStackTrace();
           System.exit(1);
       }
   }
   throw 只是终止当前线程（可能还能被捕获）；
   System.exit(x) 是强制整个 JVM 立即退出，所有线程都终止，不会走任何 catch、finally，也不会等守护线程。
   ```

8. ```
   | 对比项       | 抽象类 (`abstract class`) | 接口 (`interface`)                          |
   | --------- | ---------------------- | ----------------------------------------- |
   | 是否可包含方法实现 | ✅ 可以（包括普通方法和抽象方法）      | ✅ 可以（默认方法 default）                        |
   | 是否可包含成员变量 | ✅ 可以（可定义字段）            | ✅ 可以（只能是 `public static final` 常量）        |
   | 继承/实现关系   | 单继承（只能继承一个抽象类）         | 多实现（可以实现多个接口）                             |
   | 构造器       | ✅ 可以有构造器               | ❌ 不能有构造器                                  |
   | 用途        | 定义“**具有共性行为**”的类       | 定义“**规范或能力**”                             |
   | 访问修饰符     | 可以是 public 或 protected | 默认都是 public                               |
   | 使用场景      | 抽象一个类的父类通用部分           | 表示能力（如 `Serializable`、`Runnable`）或规范（API） |
   ```

9. ==缓存中可以是各式各样的数据，因此一般用泛型T来表示缓存中的数据类型==

10. ```
    “HashMap<Long,T>不是删掉了吗？为什么还要 release 一次？”
    删除缓存表中的引用（HashMap cache.remove(key)）只意味着你不再管理这个对象(hashmap中确实就是缓存的内容，但是删了缓存的内容，还需要后续的处理（数据写回、资源释放、线程关闭等等）)
    但对象本身可能还有副作用没处理完，比如：
    数据没写回磁盘
    资源没释放
    线程没关闭
    文件句柄没关闭
    所以：
    releaseForCache() 就是在从 cache 删除对象之后，执行的善后处理钩子
    getForCache这是从资源中获取这个数据，获得之后手动放到cache中
    ```

11. ```
    File file = new File("test.txt");
    if (file.createNewFile()) {
        System.out.println("文件创建成功");
    } else {
        System.out.println("文件已存在");
    }
    
    new File("test.txt")	仅构造一个路径对象，它表示“test.txt”这个文件可能存在，也可能不存在。它本质上是一个抽象路径名
    file.createNewFile()	真正创建文件，如果 test.txt 不存在，会在磁盘中生成一个空文件；如果存在，返回 false
    ```

12. ```
    private AtomicInteger pageNumber; 是一个 原子整数变量，常用于多线程环境中对页数的计数，它的作用主要是：
    👉 在并发环境下，安全地记录或生成页的编号（Page Number）。
    AtomicInteger counter = new AtomicInteger(0);
    counter.incrementAndGet(); // 自增，线程安全
    counter.get();             // 获取当前值
    用这个计数是线程安全的
    
    | 方法名          | 来源                 | 用途       | 说明                         |
    | ------------ | ------------------ | -------- | -------------------------- |
    | `get()`      | `AtomicInteger` 提供 | **推荐方法** | 明确表示是获取当前原子值，线程安全          |
    | `intValue()` | `Number` 父类提供      | 兼容其他数值类型 | 用于类型转换场景，比如装箱、与泛型一起使用时才有意义 |
    
    | 方法                  | 动作         | 返回值      |
    | ------------------- | ---------- | -------- |
    | `getAndIncrement()` | 先返回当前值，再自增 | **返回原值** |
    | `incrementAndGet()` | 先自增，再返回    | **返回新值** |
    
    ```

13. ```
    PageCacheImpl( int maxResource) {
        super(maxResource); // 🔹 这行是调用父类构造器
    }
    super(maxResource) 是子类构造器中调用父类构造器，把最大资源数量传给父类保存下来，从而让父类具备初始化能力。
    ```

14. ```
    在本类里面一个变量一般可以直接用，不用this来标注本类，只有在外部同名变量出现的时候，需要this.来分辨一下，否则都不用
    ```

15. ```
    数据库缓存（Buffer Pool）缓存的是磁盘上的“页面”（Page）单位的数据，也就是说，缓存的基本单位就是“页”。
    数据库缓存（也叫 Buffer Cache 或 Buffer Pool）是数据库内存中用来缓存磁盘数据页的一块空间。
    
    当你查询数据时，数据库会先从缓存中查找对应的页，如果没有，才会从磁盘读取并加载到缓存中。
    
    ```

16. ```
    了解一下磁盘存储和缓存存储
    🔁 1. 磁盘存储以页为单位（块）
    在操作系统和数据库中，磁盘不会一字节一字节地操作，而是以“块（Block）”或“页（Page）”为单位。
    
    页大小常见为 4KB、8KB、16KB 等，视系统或数据库而定。
    
    数据库文件会被划分成一个个连续编号的页，例如：
    [ Page 0 ][ Page 1 ][ Page 2 ] ...
    每个页可以存若干条记录、部分 B+ 树节点、索引、元数据等。
    
    🔁 2. 缓存（Buffer Cache）也是以页为单位管理
    数据库或操作系统会将磁盘页缓存在内存中，以减少慢速的磁盘 I/O。
    
    比如 InnoDB 的 Buffer Pool 就是一个 缓存数据页的内存区域。
    
    当你读取数据时，其实是：
    
    SQL → 找到对应页号 → 在 Buffer Pool 中查页
                             ↓
                       缓存命中则返回数据
                             ↓
                     未命中则从磁盘读入该页 → 放入 Buffer Pool
    ```

17. ```
    return new PageImpl(pano,buf.array(),this);
    这里this表示的是当前类的实例
    this 是当前类已经创建好的实例，不是创建一个新实例，所以根本不需要调用构造器！
    这里this代表就是你要用这个类，肯定是要手动创建这个类的实例（根据构造器来的），然后到里面之后，里面用到this，这个this就是自动用的就是之前创建好的这个实例（不是新创建一个）
    ```

18. ```
    public void close() {
            super.close();
            try{
                fc.close();
                file.close();
            }catch(Exception e){
                Panic.panic(e);
            }
    super.close();指的是调用父类的 close() 方法。
    ```

19. ```
    | 类名              | 关键词    | 直观含义                                |
    | --------------- | ------ | ----------------------------------- |
    | `Page`          | 接口/抽象类 | 表示“一页数据”，是逻辑上的一页                    |
    | `PageImpl`      | 实现类    | 实现了 `Page` 的具体类，包含页码、数据等信息          |
    | `PageCache`     | 接口/抽象类 | 表示“缓存页”的行为，比如获取页、释放页                |
    | `PageCacheImpl` | 实现类    | 实现了 `PageCache` 的真实缓存逻辑，操作文件、内存页    |
    | `AbstractCache` | 抽象类    | 给 `PageCacheImpl` 提供通用的缓存结构/缓存淘汰等逻辑 |
    
    ```

20. ```
    | 项目   | 页（Page）    | 缓存（PageCache）                    |
    | ---- | ---------- | -------------------------------- |
    | 本质   | 封装的数据单元    | 管理页的组件                           |
    | 包含   | 页码、数据、状态   | 页的映射表、淘汰策略、读写逻辑                  |
    | 作用   | 表示一页内容     | 提供页的获取、释放、更新、刷新功能                |
    | 生命周期 | 被缓存引用时存在   | 控制页何时加载、回收、刷新                    |
    | 示例类  | `PageImpl` | `PageCacheImpl`, `AbstractCache` |
    
    PageCache 就是专门用来“管理 Page 的”组件。
    它负责“创建页、读取页、缓存页、释放页”，让你不直接操心磁盘读写和内存管理。
    ```

21. ==看一下区别：RandomAccessFile，FileChannel操作都是磁盘上的数据，是对磁盘数据的直接操作通道==

22. ==File只是代表一个文件名或者路径，可能这个本来就不存在，但是如果到RandomAccessFile或者FileChannel就说明已经和具体的文件建立通道了==

    ```
    | 类名                                     | 类型    | 是否是真实读写通道？ | 功能                    |
    | -------------------------------------- | ----- | ---------- | --------------------- |
    | `File`                                 | 文件对象  | ❌ 否        | 表示磁盘上的一个路径或文件名（纯“名字”） |
    | `RandomAccessFile`                     | 文件流   | ✅ 是        | 可以随机读写文件内容、设置文件长度     |
    | `FileInputStream` / `FileOutputStream` | 流     | ✅ 是        | 顺序读取/写入文件内容           |
    | `FileChannel`                          | NIO通道 | ✅ 是        | 高性能、可定位、带缓冲的读写        |
    
    ```

    ```
    | 对象                 | 功能                               | 是否缓存在内存              |
    | ------------------ | -------------------------------- | -------------------- |
    | `RandomAccessFile` | 提供文件的随机访问（读/写/截断）                | ❌ 不是缓存，是真实通道         |
    | `FileChannel`      | 从 `RandomAccessFile` 中获取的 I/O 通道 | ✅ 通常是有缓冲区，但最终操作还是写磁盘 |
    | `ByteBuffer`       | 用于临时缓存数据读写                       | ✅ 是内存缓冲区，手动管理        |
    
    ```

    

23. ### 引用计数策略(QuarkDB的策略)

    - **原理**：每个对象维护一个引用计数器，记录有多少指针指向它
    - **释放时机**：当计数器归零时立即释放资源
    - **特点**：实时性高，但无法处理循环引用

    ### LRU算法

    - **原理**：基于访问时间排序，淘汰最久未使用的项目
    - **释放时机**：当空间不足时淘汰最久未使用的项目
    - **特点**：需要维护访问顺序，能处理循环引用但非实时

24. "回源"指的是**当缓存数据被释放后，如果再次需要该数据，必须回到原始数据源重新获取**的过程。

![image-20250614224814555](C:\Users\HangJ\AppData\Roaming\Typora\typora-user-images\image-20250614224814555.png)

25. 接口当然可以作返回类型，你用多态的时候都可以作为类型，比如List Set不都是接口吗！！！，用接口作为类型，暴露这个接口的方法，具体功能取决于他的实现类的的功能。
26. "页数据"（Page Data）通常指的是从**文件（如数据库文件、磁盘文件）**中读取的数据，而不是缓存中的数据。不过，具体含义可能因上下文而有所不同：
    1. **数据库系统中的页数据**
       - 在数据库（如MySQL、Oracle）中，数据存储在磁盘上的**数据文件**中，按固定大小的"页"（如16KB）组织。
       - 当查询数据时，数据库会先将磁盘上的页加载到**内存缓存（如InnoDB Buffer Pool）**中，再处理。
       - 因此，"页数据"可能指磁盘上的原始数据，也可能指缓存中的副本，具体看上下文。
    2. **操作系统的页缓存（Page Cache）**
       - 操作系统会将磁盘文件缓存在内存中（称为Page Cache），以加速读取。
       - 这里"页数据"可能指缓存中的副本，但源头仍是文件。
    3. **Web开发中的"页面数据"**
       - 如果是前端/网络相关场景，"页数据"可能指从服务器加载的静态文件（如HTML/JSON），而非浏览器缓存。

27. ### **为什么同时引入 `RandomAccessFile` 和 `FileChannel`？**

    尽管 `FileChannel` 功能更强大，但 `RandomAccessFile` 仍然被保留，可能有以下原因：

    **(1) 兼容性和便捷操作**

    - `RandomAccessFile` 提供了简单的方法（如 `readInt()`, `writeLong()` 等），适合直接操作基本数据类型，而 `FileChannel` 主要操作 `ByteBuffer`，需要额外封装。
    - 某些遗留代码可能依赖 `RandomAccessFile`，保留它可以减少重构成本。

    **(2) 获取 `FileChannel` 的方式**

    - `FileChannel` 可以通过 `RandomAccessFile.getChannel()` 获取，因此 `RandomAccessFile` 可能是为了创建 `FileChannel` 而存在的：

      ```
      RandomAccessFile file = new RandomAccessFile("data.db", "rw");
      FileChannel channel = file.getChannel(); // 通过 RandomAccessFile 获取 FileChannel
      ```

      如果直接使用 `FileChannel.open()`（NIO 方式），可能无法满足某些需求（如特定文件打开模式）。

    **(3) 功能互补**

    - `RandomAccessFile` 可以方便地获取文件长度（`file.length()`），而 `FileChannel` 更擅长高性能 I/O（如内存映射、零拷贝传输）。
    - 在代码中，`file.length()` 用于计算 `pageNumbers`，而 `FileChannel` 可能用于实际的页读写（如 `fc.read()` 或 `fc.write()`）。

28. ==注意：这里pgno和缓存中key(hashmap)中是对应的pgno是int类型，key是long类型，当把key转为int类型就变成pgno了==

29. 写回就是将数据写道磁盘里，代码中就是通过fc来实现的

30. 

31. ```
    System.arraycopy(
        RandomUtil.randomBytes(LEN_VC), // 源数组：随机生成的字节数组（长度 LEN_VC）
        0,                              // 源数组起始位置：从第 0 位开始复制
        raw,                            // 目标数组：要写入的字节数组
        OF_VC,                          // 目标数组起始位置：从 OF_VC 处开始写入
        LEN_VC                          // 复制的字节长度：LEN_VC
    );
    ```

32. ByteBuffer.wrap()于将一个 **已有的字节数组（byte[]）包装成一个 `ByteBuffer` 对象**。

33. 计算机中底层所有的数据（数据类型）==底层都是字节数组==，只不过是上层把他进行包装了，才有所谓的字符串，整数这样子

34. wrap是包裹、包装的意思

35. 

36. ```java
    try{
        file.close();
        fc.close();
    }catch (Exception e){
        Panic.panic(e);
    }
    ```

**为什么需要这样写？**

**(1) 资源泄漏风险**

如果不显式关闭文件资源：

- 文件描述符会一直被占用，导致系统资源耗尽（尤其在长时间运行的进程中）。
- 其他进程可能无法访问该文件（尤其在 Windows 系统上）。

**(2) 异常处理必要性**

- `close()` 方法可能抛出 `IOException`（如磁盘错误、文件被锁定）。
- 直接忽略异常（空 `catch` 块）会导致问题被隐藏，难以调试。

**(3) 关闭顺序**

- 先关闭 `FileChannel`，再关闭底层文件对象 RandomAccessFile是更安全的做法。
  - 某些实现中，关闭 `FileChannel` 会自动关闭底层文件，但依赖具体实现（如 `RandomAccessFile.getChannel()` 的文档说明）。
  - 显式关闭两者可以避免依赖实现细节。

![image-20250621142431249](C:\Users\HangJ\AppData\Roaming\Typora\typora-user-images\image-20250621142431249.png)

37. 合适需要加锁？

    ![image-20250621145138622](C:\Users\HangJ\AppData\Roaming\Typora\typora-user-images\image-20250621145138622.png)

38. mysql中一般一个log就代表的是一个操作，比如update，但是可能影响多行。

39. 在数据库底层设计中，**`offset` 通常就是指：**

    > ✅ **数据项（DataItem）在页面（Page）中的偏移量** —— 也就是从页的开头算起，这条数据在第几个字节处。

​	uid只是一条数据的唯一标识符，没有顺序属性在里面