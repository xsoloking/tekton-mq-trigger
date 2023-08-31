package com.solo.tekton.mq.consumer.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.TaskLog;
import com.solo.tekton.mq.consumer.handler.BaseTask;
import com.solo.tekton.mq.consumer.handler.RuntimeInfo;
import com.solo.tekton.mq.consumer.handler.TaskFactory;
import com.solo.tekton.mq.consumer.service.LogService;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static com.solo.tekton.mq.consumer.utils.Common.getParams;


@Component
@Slf4j
public class BuildListener {

    @Autowired
    KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    String namespace;

    @Value("${flow.mq.exchange}")
    String exchange;

    @Value("${flow.mq.routing.key.logging}")
    String routingKey;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    LogService logService;

    @RabbitListener(queues = "${flow.mq.queue.triggered}")
    public void receiveMessage(byte[] body) throws IOException {
        log.info("Received message: " + new String(body));
        ObjectMapper mapper = new ObjectMapper();
        RuntimeInfo runtimeInfo = mapper.readValue(body, RuntimeInfo.class);
        BaseTask task = TaskFactory.createTask(runtimeInfo);
        TaskLog taskLog = this.generateTaskLog(runtimeInfo);
        Long taskInstanceId = taskLog.getTaskInstanceId();
        try {
            task.createPipelineRun(kubernetesClient, namespace);
            //TODO send taskLog as message to rabbitmq, and set the content type to application/json
            MessageProperties properties = new MessageProperties();
            properties.setContentType("application/json");
            rabbitTemplate.convertAndSend(exchange, routingKey, taskLog);
            log.info("Create pipelineRun for task \"{}:{}\" was successful", runtimeInfo.getProject(), taskInstanceId);
            taskLog.setLogContent("Start to run task \"" + runtimeInfo.getProject() + " \"");
            logService.insertLogToMongo(taskLog);
        } catch (RuntimeException e) {
            log.error("Create pipelineRun for task \"{}:{}\"  was failed with an exception: {}",
                    runtimeInfo.getProject(), taskInstanceId, e);
            taskLog = this.generateTaskLog(runtimeInfo);
            taskLog.setLogContent("Failed to run task \"" + runtimeInfo.getProject() + " \"");
            logService.insertLogToMongo(taskLog);
            taskLog = this.generateTaskLog(runtimeInfo);
            taskLog.setLogContent("Due to exception \"" + e.getMessage() + " \"");
            logService.insertLogToMongo(taskLog);
        }
    }

    private TaskLog generateTaskLog(RuntimeInfo runtimeInfo) {
        TaskLog taskLog = new TaskLog();
        Map<String, String> params = getParams(runtimeInfo);
        taskLog.setExecuteBatchId(Long.parseLong(params.get("executeBatchId")));
        taskLog.setFlowInstanceId(Long.parseLong(params.get("flowInstanceId")));
        taskLog.setNodeInstanceId(Long.parseLong(params.get("nodeInstanceId")));
        taskLog.setTaskInstanceId(Long.parseLong(params.get("taskInstanceId")));
        taskLog.setTimeout(Long.parseLong(params.get("TASK_TIMEOUT")));
        return taskLog;
    }
}
