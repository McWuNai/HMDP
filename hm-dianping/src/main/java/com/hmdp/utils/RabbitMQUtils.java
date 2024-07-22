package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
@Component
@Slf4j
public class RabbitMQUtils {
    @Resource
    RabbitTemplate rabbitTemplate;

    public void send(String EXCHANGE_DIRECT, String ROUTING_KEY, Object... message) {
        rabbitTemplate.convertAndSend(EXCHANGE_DIRECT, ROUTING_KEY, message);
    }
}
