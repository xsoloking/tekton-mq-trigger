package com.solo.tekton.mq.consumer.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.handler.BaseTask;
import com.solo.tekton.mq.consumer.handler.RuntimeInfo;
import com.solo.tekton.mq.consumer.handler.TaskFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.fabric8.tekton.pipeline.v1.*;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;


@Component
@Slf4j
public class BuildListener {

    @Autowired
    TektonClient tektonClient;

    @Autowired
    KubernetesClient kubernetesClient;

    @RabbitListener(queues = "tasks-triggered")
    public void receiveMessage(byte[] body) throws IOException, ParseException {
        log.info("Received message: " + new String(body));
        ObjectMapper mapper = new ObjectMapper();
        RuntimeInfo runtimeInfo = mapper.readValue(body, RuntimeInfo.class);
        BaseTask task = TaskFactory.createTask(runtimeInfo);
        if(task.prepareResources(kubernetesClient)) {
            if(task.createPipelineRun(tektonClient)) {
                log.info("TODO");
            } else {
                log.error("TOD");
            }
        } else {
            log.error("TOD");
        }
    }

}
