package com.example.delaytask.redis.zset;

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
                if( num != null && num > 0) {
                    log.info("order {} timeout and was canceled at {}", orderId, currentTime);
                }
            }
        }
    }
}
