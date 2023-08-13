package com.solo.tekton.mq.consumer.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class BuildListener {

    @RabbitListener(queues = "tasks-triggered")
    public void receiveMessage(byte[] body) {
        log.info("Received message: " + new String(body));
    }

}
