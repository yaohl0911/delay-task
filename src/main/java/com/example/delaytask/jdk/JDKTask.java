package com.example.delaytask.jdk;

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