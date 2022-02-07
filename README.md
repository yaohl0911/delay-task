# delay-task

在开发中，往往会遇到一些关于延时任务的需求。例如

- 生成订单30分钟未支付，则自动取消
- 生成订单60秒后,给用户发短信

对上述的任务，用专业的名字来描述就是延时任务。那么这里就会产生一个问题，延时任务和定时任务的区别究竟在哪里呢？一共有如下几点区别：

- 定时任务有明确的触发时间，延时任务没有

- 定时任务有执行周期，而延时任务在某事件触发后一段时间内执行，没有执行周期

- 定时任务一般执行的是批处理操作是多个任务，而延时任务一般是单个任务

下面我们以判断订单是否超时为例，进行方案分析

## 方案分析

### 数据库轮询

#### 思路

该方案通常是在小型项目中使用，即通过一个线程定时的去扫描数据库，通过订单时间来判断是否有超时的订单，然后进行update或delete等操作

#### 实现

quartz来实现，简单介绍一下

maven项目引入一个依赖如下所示

```xml
<dependency>
    <groupId>org.quartz-scheduler</groupId>
    <artifactId>quartz</artifactId>
</dependency>
```

调用Demo类MyJob如下所示

```java
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

@Slf4j
public class QuartzTask implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        log.info("start to scan db to get task...");
    }

    public static void main(String[] args) throws Exception {
        // 创建任务
        JobDetail jobDetail = JobBuilder
            .newJob(QuartzTask.class)
            .withIdentity("quartz-job", "group1")
            .build();

        // 创建触发器 每3秒钟执行一次
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("trigger1", "group3")
            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(3).repeatForever())
            .build();
        Scheduler scheduler = new StdSchedulerFactory().getScheduler();

        // 将任务及其触发器放入调度器
        scheduler.scheduleJob(jobDetail, trigger);
        // 调度器开始调度任务
        scheduler.start();
    }

}
```

运行代码，可发现每隔3秒，输出如下

`start to scan db to get task...`

**优缺点**

- 优点: 
  - 简单易行
  - 支持集群操作
- 缺点:
  - 对服务器内存消耗大
  - 存在延迟，比如你每隔3分钟扫描一次，那最坏的延迟时间就是3分钟
  - 假设你的订单有几千万条，每隔几分钟这样扫描一次，数据库损耗极大

注：Quartz有更复杂的使用方式，能解决大型项目的问题，但是目前对NoSQL支持好像不是很好，猜测是NoSQL的锁不太好实现。

### JDK的延迟队列

#### 思路

该方案是利用JDK自带的DelayQueue来实现，这是一个无界阻塞队列，该队列只有在延迟期满的时候才能从中获取元素，放入DelayQueue中的对象，是必须实现Delayed接口的。

DelayedQueue实现工作流程如下图所示

![图片](https://mmbiz.qpic.cn/mmbiz_jpg/8Jeic82Or04nVtDbBjwqU4cP1FOKGBH19uNWAZRvIiaiahK4EiasVJ0FQI5Y2ACtuGBSXvQnSI46nYicZRxfQicAqsJw/640?wx_fmt=jpeg&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

其中Poll():获取并移除队列的超时元素，没有则返回空

take():获取并移除队列的超时元素，如果没有则wait当前线程，直到有元素满足超时条件，返回结果。

#### 实现

定义一个类OrderDelay实现Delayed，代码如下

```java
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JDKTask implements Delayed {

    private String orderId;

    private long delayTime;

    public JDKTask(String orderId, long timeout) {
        this.orderId = orderId;
        delayTime = System.currentTimeMillis() + timeout;
    }

    // 返回距离你自定义的超时时间还有多少
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delayTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        if (other == this) {
            return 0;
        }

        JDKTask task = (JDKTask)other;
        long delta = (this.getDelay(TimeUnit.MILLISECONDS) - task.getDelay(TimeUnit.MILLISECONDS));
        return (delta == 0) ? 0 : ((delta < 0) ? -1 : 1);
    }

    void print() {
        log.info("order {} will be deleted.", orderId);
    }

    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("00000000");
        list.add("00000001");
        list.add("00000002");
        list.add("00000003");
        list.add("00000004");

        DelayQueue<JDKTask> queue = new DelayQueue<>();

        long start = System.currentTimeMillis();

        for(int i = 0; i < 5; i++){
            //延迟三秒取出
            queue.put(new JDKTask(list.get(i), TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS)));
            try {
                queue.take().print();
                log.info("after {} milli seconds", System.currentTimeMillis() - start);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
```

输出如下

```bash
order 00000000 will be deleted.
after 5011 milli seconds
order 00000001 will be deleted.
after 10016 milli seconds
order 00000002 will be deleted.
after 15020 milli seconds
order 00000003 will be deleted.
after 20026 milli seconds
order 00000004 will be deleted.
after 25032 milli seconds
```

可以看到都是延迟3秒，订单被删除

**优缺点**

- 优点:
  - 效率高
  - 任务触发时间延迟低

- 缺点:
  - 服务器重启后，数据全部消失，怕宕机
  - 集群扩展相当麻烦
  - 因为内存条件限制的原因，比如下单未付款的订单数太多，那么很容易就出现OOM异常
  - 代码复杂度较高

### 时间轮算法

#### 思路

先上一张时间轮的图(这图到处都是啦)

![图片](https://mmbiz.qpic.cn/mmbiz_jpg/8Jeic82Or04nVtDbBjwqU4cP1FOKGBH19BsyH0afkiacSvIMFWYgyenXrGnRxRzicvPOiaPib4INfu5hFia0CpFuBrYQ/640?wx_fmt=jpeg&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

时间轮算法可以类比于时钟，如上图箭头（指针）按某一个方向按固定频率轮动，每一次跳动称为一个 tick。这样可以看出定时轮由个3个重要的属性参数，ticksPerWheel（一轮的tick数），tickDuration（一个tick的持续时间）以及 timeUnit（时间单位），例如当ticksPerWheel=60，tickDuration=1，timeUnit=秒，这就和现实中的始终的秒针走动完全类似了。

如果当前指针指在1上面，我有一个任务需要4秒以后执行，那么这个执行的线程回调或者消息将会被放在5上。那如果需要在20秒之后执行怎么办，由于这个环形结构槽数只到8，如果要20秒，指针需要多转2圈。位置是在2圈之后的5上面（20 % 8 + 1）

#### 实现

我们用Netty的HashedWheelTimer来实现

给Pom加上下面的依赖

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-common</artifactId>
</dependency>
```

测试代码HashedWheelTimerTest如下所示

```java
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor
public class WheelTimer implements TimerTask {

        boolean flag;

        @Override
        public void run(Timeout timeout) throws Exception {
            log.info("start to delete the order");
            this.flag =false;
        }

    public static void main(String[] argv) {

        WheelTimer timerTask = new WheelTimer(true);

        Timer timer = new HashedWheelTimer();

        timer.newTimeout(timerTask, 5, TimeUnit.SECONDS);

        int i = 1;

        while(timerTask.flag){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("{} seconds passed", i);
            i++;
        }
    }
}
```

输出如下

```bash
[main] INFO com.example.delaytask.wheel.WheelTimer - 1 seconds passed
[main] INFO com.example.delaytask.wheel.WheelTimer - 2 seconds passed
[main] INFO com.example.delaytask.wheel.WheelTimer - 3 seconds passed
[main] INFO com.example.delaytask.wheel.WheelTimer - 4 seconds passed
[main] INFO com.example.delaytask.wheel.WheelTimer - 5 seconds passed
[pool-1-thread-1] INFO com.example.delaytask.wheel.WheelTimer - start to delete the order
[main] INFO com.example.delaytask.wheel.WheelTimer - 6 seconds passed
```

**优缺点**

- 优点:
  - 效率高
  - 任务触发时间延迟时间比delayQueue低
  - 代码复杂度比delayQueue低。

- 缺点:
  - 服务器重启后，数据全部消失，怕宕机
  - 集群扩展相当麻烦
  - 因为内存条件限制的原因，比如下单未付款的订单数太多，那么很容易就出现OOM异常

### redis缓存

#### 思路一

利用redis的zset,zset是一个有序集合，每一个元素(member)都关联了一个score,通过score排序来取集合中的值

添加元素:`ZADD key score member [[score member] [score member] …]`

按顺序查询元素:`ZRANGE key start stop [WITHSCORES]`

查询元素`score:ZSCORE key member`

移除元素:`ZREM key member [member …]`

测试如下

```bash
# 添加单个元素
redis> ZADD page_rank 10 google.com
(integer) 1
# 添加多个元素
redis> ZADD page_rank 9 baidu.com 8 bing.com
(integer) 2
redis> ZRANGE page_rank 0 -1 WITHSCORES
1) "bing.com"
2) "8"
3) "baidu.com"
4) "9"
5) "google.com"
6) "10"
# 查询元素的score值
redis> ZSCORE page_rank bing.com
"8"
# 移除单个元素
redis> ZREM page_rank google.com
(integer) 1
redis> ZRANGE page_rank 0 -1 WITHSCORES
1) "bing.com"
2) "8"
3) "baidu.com"
4) "9"
```

那么如何实现呢？我们将订单超时时间戳与订单号分别设置为score和member,系统扫描第一个元素判断是否超时，具体如下图所示

![图片](https://mmbiz.qpic.cn/mmbiz_jpg/8Jeic82Or04nVtDbBjwqU4cP1FOKGBH195xAJwiaiat8iaqtREDbjz0LHxNXreH3dgA2TeFw7ucgnoatIrXgib3TnhQ/640?wx_fmt=jpeg&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

#### 实现一

Provider

```java
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

@Slf4j
public class Provider {

    private static final String ORDERPREFIX = "OID0000000";

    public void provideOrder() {
        long i = 0;
        while (true) {
            //每隔2s生成一个订单，10s之后取消
            long currentTime = System.currentTimeMillis();
            long expiredTime = currentTime + 10000;
            String orderId = ORDERPREFIX + i;
            Jedis jedis = JedisUtils.getJedis();
            jedis.zadd("OrderId", (double) expiredTime / 1000, orderId);
            log.info("order {} generated at time {}, and will expire at {}", orderId, currentTime, expiredTime);
            jedis.close();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            i++;
        }
    }
}
```

Consumer

```java
package com.example.delaytask.redis;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Tuple;

import java.util.Set;

@Slf4j
public class Consumer  {

    public void consumerDelayMessage() {
        Jedis jedis = JedisUtils.getJedis();
        while(true) {
            Set<Tuple> items = jedis.zrangeWithScores("OrderId", 0, 1);
            if(items == null || items.isEmpty()) {
                log.info("no task in the queue");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            int  score = (int)((Tuple)items.toArray()[0]).getScore();
            long currentTime = System.currentTimeMillis();
            if(((double)currentTime / 1000) >= score) {
                String orderId = ((Tuple)items.toArray()[0]).getElement();
                Long num = jedis.zrem("OrderId", orderId);
                log.info("order {} timeout and was canceled at {}", orderId, currentTime);
            }
        }
    }
}
```

只有一个消费者时

```java
import java.util.concurrent.CountDownLatch;

public class ThreadTest {

    private static final int threadNum = 1;

    private static CountDownLatch cdl = new CountDownLatch(threadNum);

    static class DelayMessage implements Runnable{
        public void run() {
            try {
                cdl.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Consumer consumer = new Consumer();
            consumer.consumerDelayMessage();
        }
    }

    public static void main(String[] args) {

        Provider provider = new Provider();

        for(int i = 0; i < threadNum; i++){
            new Thread(new DelayMessage()).start();
            cdl.countDown();
        }

        provider.provideOrder();
    }
}
```

此时对应输出如下

```bash
[main] INFO com.example.delaytask.redis.Provider - order OID00000001 generated at time 1644245345724, and will expire at 1644245
[main] INFO com.example.delaytask.redis.Provider - order OID00000002 generated at time 1644245347729, and will expire at 1644245
[main] INFO com.example.delaytask.redis.Provider - order OID00000003 generated at time 1644245349735, and will expire at 1644245
[main] INFO com.example.delaytask.redis.Provider - order OID00000004 generated at time 1644245351739, and will expire at 1644245
[Thread-0] INFO com.example.delaytask.redis.Consumer - order OID00000000 timeout and was canceled at 1644245353000
[main] INFO com.example.delaytask.redis.Provider - order OID00000005 generated at time 1644245353744, and will expire at 1644245
[Thread-0] INFO com.example.delaytask.redis.Consumer - order OID00000001 timeout and was canceled at 1644245355000
[main] INFO com.example.delaytask.redis.Provider - order OID00000006 generated at time 1644245355749, and will expire at 1644245
[Thread-0] INFO com.example.delaytask.redis.Consumer - order OID00000002 timeout and was canceled at 1644245357000
```

可以看到，几乎都是3秒之后，消费订单。

然而，这一版存在一个致命的硬伤，在高并发条件下，多消费者会取到同一个订单号，我们上测试代码ThreadTest

```java
import java.util.concurrent.CountDownLatch;

public class ThreadTest {
    // TODO: 2022/2/7 jedis-pool默认连接数是8，线程数较多时注意修改大一点 
    private static final int threadNum = 3;

    private static CountDownLatch cdl = new CountDownLatch(threadNum);

    static class DelayMessage implements Runnable{
        public void run() {
            try {
                cdl.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Consumer consumer = new Consumer();
            consumer.consumerDelayMessage();
        }
    }

    public static void main(String[] args) {

        Provider provider = new Provider();

        for(int i = 0; i < threadNum; i++){
            new Thread(new DelayMessage()).start();
            cdl.countDown();
        }

        provider.provideOrder();
    }
}
```

输出如下所示

```bash
[main] INFO com.example.delaytask.redis.Provider - order OID00000001 generated at time 1644245686177, and will expire at 1644245696177
[main] INFO com.example.delaytask.redis.Provider - order OID00000002 generated at time 1644245688182, and will expire at 1644245698182
[main] INFO com.example.delaytask.redis.Provider - order OID00000003 generated at time 1644245690188, and will expire at 1644245700188
[main] INFO com.example.delaytask.redis.Provider - order OID00000004 generated at time 1644245692193, and will expire at 1644245702193
[Thread-0] INFO com.example.delaytask.redis.Consumer - order OID00000000 timeout and was canceled at 1644245693000
[Thread-2] INFO com.example.delaytask.redis.Consumer - order OID00000000 timeout and was canceled at 1644245693000
[Thread-1] INFO com.example.delaytask.redis.Consumer - order OID00000000 timeout and was canceled at 1644245693000
[main] INFO com.example.delaytask.redis.Provider - order OID00000005 generated at time 1644245694198, and will expire at 1644245704198
[Thread-0] INFO com.example.delaytask.redis.Consumer - order OID00000001 timeout and was canceled at 1644245696000
[Thread-1] INFO com.example.delaytask.redis.Consumer - order OID00000001 timeout and was canceled at 1644245696000
[Thread-2] INFO com.example.delaytask.redis.Consumer - order OID00000001 timeout and was canceled at 1644245696000
[main] INFO com.example.delaytask.redis.Provider - order OID00000006 generated at time 1644245696203, and will expire at 1644245706203
[Thread-0] INFO com.example.delaytask.redis.Consumer - order OID00000002 timeout and was canceled at 1644245698000
[Thread-1] INFO com.example.delaytask.redis.Consumer - order OID00000002 timeout and was canceled at 1644245698000
[Thread-2] INFO com.example.delaytask.redis.Consumer - order OID00000002 timeout and was canceled at 1644245698000
```

显然，出现了多个线程消费同一个资源的情况。

解决方案

(1)用分布式锁，但是用分布式锁，性能下降了，该方案不细说。

(2)对ZREM的返回值进行判断，只有大于0的时候，才消费数据，于是将consumerDelayMessage()方法里的

```java
if(((double)currentTime / 1000) >= score) {
  String orderId = ((Tuple)items.toArray()[0]).getElement();
  Long num = jedis.zrem("OrderId", orderId);
  log.info("order {} timeout and was canceled at {}", orderId, currentTime);
}
```

修改为

```java
if(((double)currentTime / 1000) >= score) {
  String orderId = ((Tuple)items.toArray()[0]).getElement();
  Long num = jedis.zrem("OrderId", orderId);
  if( num != null && num > 0) {
    log.info("order {} timeout and was canceled at {}", orderId, currentTime);
  }
}
```

在这种修改后，重新运行ThreadTest类，发现输出正常了

```bash
[main] INFO com.example.delaytask.redis.Provider - order OID00000001 generated at time 1644245828803, and will expire at 1644245838803
[main] INFO com.example.delaytask.redis.Provider - order OID00000002 generated at time 1644245830809, and will expire at 1644245840809
[main] INFO com.example.delaytask.redis.Provider - order OID00000003 generated at time 1644245832814, and will expire at 1644245842814
[main] INFO com.example.delaytask.redis.Provider - order OID00000004 generated at time 1644245834819, and will expire at 1644245844819
[Thread-1] INFO com.example.delaytask.redis.Consumer - order OID00000000 timeout and was canceled at 1644245836000
[main] INFO com.example.delaytask.redis.Provider - order OID00000005 generated at time 1644245836824, and will expire at 1644245846824
[Thread-2] INFO com.example.delaytask.redis.Consumer - order OID00000001 timeout and was canceled at 1644245838000
[main] INFO com.example.delaytask.redis.Provider - order OID00000006 generated at time 1644245838828, and will expire at 1644245848828
[Thread-1] INFO com.example.delaytask.redis.Consumer - order OID00000002 timeout and was canceled at 1644245840000
```

#### 思路二

该方案使用redis的Keyspace Notifications，中文翻译就是键空间机制，就是利用该机制可以在key失效之后，提供一个回调，实际上是redis会给客户端发送一个消息。是需要redis版本2.8以上。

#### 实现二

在redis中，执行`config set notify-keyspace-events Ex`

运行代码如下

```java
import com.example.delaytask.redis.zset.JedisUtils;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.text.SimpleDateFormat;

@Slf4j
public class RedisNamespace {

    public static final String _TOPIC = "__keyevent@0__:expired";  // 订阅频道名称

    public static void main(String[] args) {
        Jedis jedis = JedisUtils.getJedis();
        for(int i = 0; i < 5; i++){
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String orderId = "OID000000" + i;
            jedis.setex(orderId, 10L, orderId);
            log.info("order {} created at {}", orderId, simpleDateFormat.format(System.currentTimeMillis()));
        }
        // 执行定时任务
        doTask(jedis);
    }

    public static void doTask(Jedis jedis) {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // 订阅过期消息
        jedis.psubscribe(new JedisPubSub() {
            @Override
            public void onPMessage(String pattern, String channel, String message) {
                // 接收到消息，执行定时任务
                log.info("message {} deleted at {}", message, simpleDateFormat.format(System.currentTimeMillis()));
            }
        }, _TOPIC);
    }
}
```

输出如下

```bash
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - order OID0000000 created at 2022-02-07 23:04:27
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - order OID0000001 created at 2022-02-07 23:04:27
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - order OID0000002 created at 2022-02-07 23:04:27
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - order OID0000003 created at 2022-02-07 23:04:27
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - order OID0000004 created at 2022-02-07 23:04:27
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - message OID0000000 deleted at 2022-02-07 23:04:37
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - message OID0000003 deleted at 2022-02-07 23:04:37
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - message OID0000002 deleted at 2022-02-07 23:04:37
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - message OID0000001 deleted at 2022-02-07 23:04:37
[main] INFO com.example.delaytask.redis.namespace.RedisNamespace - message OID0000004 deleted at 2022-02-07 23:04:37
```

可以明显看到3秒过后，收到了消息被删除的通知

redis的pub/sub机制存在一个硬伤，官网内容如下

原文:

> Because Redis Pub/Sub is fire and forget currently there is no way to use this feature if your application demands reliable notification of events, that is, if your Pub/Sub client disconnects, and reconnects later, all the events delivered during the time the client was disconnected are lost.

翻译:

> Redis的发布/订阅目前是即发即弃(fire and forget)模式的，因此无法实现事件的可靠通知。也就是说，如果发布/订阅的客户端断链之后又重连，则在客户端断链期间的所有事件都丢失了。因此，方案二不是太推荐。当然，如果你对可靠性要求不高，可以使用。

**优缺点**

- 优点:
  - 由于使用Redis作为消息通道，消息都存储在Redis中。如果发送程序或者任务处理程序挂了，重启之后，还有重新处理数据的可能性。
  - 做集群扩展相当方便
  - 时间准确度高

- 缺点:
  - 需要额外进行redis维护
  - redis发布订阅模式可靠性不高

### 使用消息队列

#### 思路

我们可以采用rabbitMQ的延时队列。

RabbitMQ具有以下两个特性，可以实现延迟队列:

- RabbitMQ可以针对Queue和Message设置 x-message-ttl，来控制消息的生存时间，如果超时，则消息变为dead letter

- RabbitMQ的Queue可以配置x-dead-letter-exchange 和x-dead-letter-routing-key（可选）两个参数，用来控制队列内出现了dead letter，则按照这两个参数重新路由。结合以上两个特性，就可以模拟出延迟消息的功能。

**优缺点**

优点: 高效,可以利用rabbitmq的分布式特性轻易的进行横向扩展,消息支持持久化增加了可靠性。

缺点：本身的易用度要依赖于rabbitMq的运维.因为要引用rabbitMq,所以复杂度和成本变高

<img width="510" alt="image-20220205152300329" src="https://user-images.githubusercontent.com/33289635/152637016-7512d3c5-1221-401c-9580-30283530ec77.png">

路由规则：

- fanout：即广播形式，把消息发送到所有的队列中
- direct：直接按照route-key直接与对应队列绑定，精确查询
- topic：按照一定规则匹配，类似于模糊查询，比direct方式更加灵活

存活时间，即Time-To-Live（TTL）：

- x-message-ttl:创建队列时指定，设置队列中所有消息的的ttl（队列粒度）
- x-expire:创建队列时指定，设置消息超时时间，未使用时间超过该周期后过期（队列粒度）
- AMQP.BasicProperties:随同消息指定，设置当前消息的ttl（消息粒度）

死信交换，即Dead Letter Exchange（DLX）：

- 死信：死亡/无效消息
  - 消息被拒
  - 消息ttl过期
  - 超出队列长度
- 交换：RabbitMQ自动将死信交换到其他队列，前提是要指定目标交换器

#### 实现

交换器，队列及绑定关系创建

```java
@Configuration
public class RabbitMQConfiguration {

    // 创建延时队列
    @Bean
    public Queue delayQueue() {
        Map<String, Object> params = new HashMap<>();
        // 声明队列里的死信转发到的交换器的名称
        params.put("x-dead-letter-exchange", Constants.IMMEDIATE_EXCHANGE);
        // 声明死信在转发时携带的routing-key
        params.put("x-dead-letter-routing-key", Constants.IMMEDIATE_ROUTING_KEY);
        return new Queue(Constants.DELAY_QUEUE, true, false, false, params);
    }

    // 创建即时队列
    @Bean
    public Queue immediateQueue() {
        // 第一个参数是队列的名称，第二个参数指定是否支持持久化
        return new Queue(Constants.IMMEDIATE_QUEUE, true);
    }

    // 创建延时消息交换器
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(Constants.DELAY_EXCHANGE, true, false);
    }

    // 创建即时消息交换器
    @Bean
    public DirectExchange immediateExchange() {
        return new DirectExchange(Constants.IMMEDIATE_EXCHANGE, true, false);
    }

    // 创建延时队列绑定关系，把延时队列绑定到延时交换器
    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(deadLetterExchange()).with(Constants.DELAY_ROUTING_KEY);
    }

    // 创建即时队列绑定关系，把即时队列绑定到即时交换器
    @Bean
    public Binding immediateBinding() {
        return BindingBuilder.bind(immediateQueue()).to(immediateExchange()).with(Constants.IMMEDIATE_ROUTING_KEY);
    }

}
```

生产者实现

```java
@Slf4j
@Component
public class ImmediateSender {

    private final RabbitTemplate rabbitTemplate;

    public ImmediateSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(String name, int delayTime) {
        log.info("name {}, delay time {}", name, delayTime);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitTemplate.convertAndSend(Constants.DELAY_EXCHANGE, Constants.DELAY_ROUTING_KEY, name, message -> {
            message.getMessageProperties().setExpiration(delayTime + "");
            log.info("delay message send at {}", dateFormat.format(new Date()));
            return message;
        });
    }
}
```

消费者实现

```java
@Slf4j
@Component
public class ImmediateReceiver {
    @RabbitListener(queues = Constants.IMMEDIATE_QUEUE) // 对应队列有消息时，自动调用该方法
    // @RabbitHandler // 监听器的处理方法，根据类型匹配（监听器调用时）
    public void get(String name) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("get delay message {} at {}", name, dateFormat.format(new Date()));
    }
}
```

测试代码

```java
@SpringBootTest
public class RabbitMQTimeoutTest {

    @Autowired
    private ImmediateSender sender;

    @Test
    public void test1() {
        sender.send("test1", 5000);
        sender.send("test2", 10000);
        // 存在问题，RabbitMQ默认优先处理队头的消息，只有处理完之后才处理之后的消息
        // 解决方案，RabbitMQ服务器安装x-delay-message插件，之后会从所有消息中取死信，而不是从头依次处理
        sender.send("test3", 2000);

        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

#### 问题

存在问题：RabbitMQ默认优先处理队头的消息，只有处理完之后才处理之后的消息
解决方案：RabbitMQ服务器安装x-delay-message插件，之后会从所有消息中取死信，而不是从头依次处理


# 参考资料
  [生成订单30分钟未支付，则自动取消，该怎么实现？](https://mp.weixin.qq.com/s/98p3t_Jyf-9uXuOxenBKlQ)
  [史上最全的延迟任务实现方式汇总！附代码（强烈推荐)](https://www.cnblogs.com/vipstone/p/12696465.html)
  
        sender.send("test3", 2000);
