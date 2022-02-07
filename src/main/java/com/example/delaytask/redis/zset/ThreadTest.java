package com.example.delaytask.redis.zset;

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
