package com.example.delaytask.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfiguration {

    // 创建延时队列
    @Bean
    public Queue delayQueue() {
        Map<String, Object> params = new HashMap<>();
        // 声明队列里的死信转发到的交换器的名称
        params.put("x-dead-letter-exchange", Constants.IMMEDIATE_EXCHANGE);
        // 声明死信在转发时携带的routing-key
        params.put("x-dead-letter-routing-key", Constants.IMMEDIATE_ROUTING_KEY);
        return new Queue(Constants.DELAY_QUEUE, true, false, false, params);
    }

    // 创建即时队列
    @Bean
    public Queue immediateQueue() {
        // 第一个参数是队列的名称，第二个参数指定是否支持持久化
        return new Queue(Constants.IMMEDIATE_QUEUE, true);
    }

    // 创建延时消息交换器
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(Constants.DELAY_EXCHANGE, true, false);
    }

    // 创建即时消息交换器
    @Bean
    public DirectExchange immediateExchange() {
        return new DirectExchange(Constants.IMMEDIATE_EXCHANGE, true, false);
    }

    // 创建延时队列绑定关系，把延时队列绑定到延时交换器
    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(deadLetterExchange()).with(Constants.DELAY_ROUTING_KEY);
    }

    // 创建即时队列绑定关系，把即时队列绑定到即时交换器
    @Bean
    public Binding immediateBinding() {
        return BindingBuilder.bind(immediateQueue()).to(immediateExchange()).with(Constants.IMMEDIATE_ROUTING_KEY);
    }

}
