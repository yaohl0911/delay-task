package com.example.delaytask.redis.zset;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class JedisUtils {
    private static final String ADDR = "127.0.0.1";

    private static final int PORT = 6379;

    private static JedisPool jedisPool = new JedisPool(ADDR, PORT);

    public static Jedis getJedis() {
        return jedisPool.getResource();
    }
}
