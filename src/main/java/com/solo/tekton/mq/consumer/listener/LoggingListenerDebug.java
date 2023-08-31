package com.solo.tekton.mq.consumer.listener;

import com.solo.tekton.mq.consumer.service.LogService;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class LoggingListenerDebug {

    @Autowired
    KubernetesClient kubernetesClient;

    @Autowired
    LogService logService;

    @RabbitListener(queues = "tasks-logging-debug")
    public void receiveMessage(String podName) throws IOException, InterruptedException {
        logService.redirectLogs(podName, "default", 30);
    }


}
