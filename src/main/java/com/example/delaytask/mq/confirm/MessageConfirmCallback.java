package com.example.delaytask.mq.confirm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
public class MessageConfirmCallback implements RabbitTemplate.ConfirmCallback {
    @Override
    public void confirm(CorrelationData correlationData, boolean b, String s) {
        if(b) {
            log.info("message receive success");
        } else {
            log.error("message receive failed");
        }
    }
}