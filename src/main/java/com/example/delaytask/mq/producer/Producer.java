package com.example.delaytask.mq.producer;

import com.example.delaytask.mq.config.Constants;
import com.example.delaytask.mq.confirm.MessageConfirmCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Component
public class Producer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void regCallback() {
        rabbitTemplate.setConfirmCallback(new MessageConfirmCallback());
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