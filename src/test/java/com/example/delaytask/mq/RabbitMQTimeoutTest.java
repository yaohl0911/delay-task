package com.example.delaytask.mq;

import com.example.delaytask.mq.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RabbitMQTimeoutTest {

    @Autowired
    private Producer producer;

    @Test
    public void test1() {
        producer.send("test1", 5000);
        producer.send("test2", 10000);
        // 存在问题，RabbitMQ默认优先处理队头的消息，只有处理完之后才处理之后的消息
        // 解决方案，RabbitMQ服务器安装插件：x-delay-message插件，之后回从所有消息中取死信，而不是从头依次处理
        producer.send("test3", 2000);

        try {
            Thread.sleep(35000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
