package com.example.delaytask.mq.consumer;

import com.example.delaytask.mq.config.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.channels.Channel;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Component
public class Consumer {
    @RabbitListener(queues = Constants.IMMEDIATE_QUEUE) // 对应队列有消息时，自动调用该方法
//    @RabbitHandler // 监听器的处理方法，根据类型匹配（监听器调用时）
    public void get(String msg) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            log.info("get delay message {} at {}", msg, dateFormat.format(new Date()));
//        log.info("get delay message {} at {}, {}", name, dateFormat.format(new Date()), 1/0);  // 验证重试次数是否好用
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }
}