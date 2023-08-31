package com.solo.tekton.mq.consumer.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.handler.BaseTask;
import com.solo.tekton.mq.consumer.handler.RuntimeInfo;
import com.solo.tekton.mq.consumer.handler.TaskFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
@Slf4j
public class BuildListener {

    @Autowired
    KubernetesClient kubernetesClient;

    @RabbitListener(queues = "tasks-triggered")
    public void receiveMessage(byte[] body) throws IOException {
        log.info("Received message: " + new String(body));
        ObjectMapper mapper = new ObjectMapper();
        RuntimeInfo runtimeInfo = mapper.readValue(body, RuntimeInfo.class);
        BaseTask task = TaskFactory.createTask(runtimeInfo);

        if(task.createPipelineRun(kubernetesClient)) {
            log.info("TODO: write log");
        } else {
            log.error("TOD");
        }
    }

}
