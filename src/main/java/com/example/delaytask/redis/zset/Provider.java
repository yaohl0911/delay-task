package com.example.delaytask.redis.zset;

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
