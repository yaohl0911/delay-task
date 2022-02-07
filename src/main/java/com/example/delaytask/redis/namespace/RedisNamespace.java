package com.example.delaytask.redis.namespace;

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
