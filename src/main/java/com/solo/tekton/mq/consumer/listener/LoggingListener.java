package com.solo.tekton.mq.consumer.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.TaskLog;
import com.solo.tekton.mq.consumer.service.LogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoggingListener {

    @Autowired
    LogService logService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${flow.mq.queue.logging}", durable = "true"),
            exchange = @Exchange(value = "${flow.mq.exchange}", type = ExchangeTypes.DIRECT),
            key = "${flow.mq.routing.key.logging}"
    ))
    public void onMessage(String message) throws JsonProcessingException {
        log.info("Received message: " + message);
        TaskLog taskLog = new ObjectMapper().readValue(message, TaskLog.class);
        logService.redirectLogs(taskLog);
    }
//    public void receiveMessage(byte[] body) throws IOException {
//        String msg = new String(body);
//        log.info("Received message: " + msg);
//        ObjectMapper mapper = new ObjectMapper();
//        TaskLog taskLog = mapper.readValue(msg, TaskLog.class);
//        logService.redirectLogs(taskLog);
//    }
}
