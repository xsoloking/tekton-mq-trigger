package com.solo.tekton.mq.consumer.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.TaskLog;
import com.solo.tekton.mq.consumer.handler.BaseTask;
import com.solo.tekton.mq.consumer.handler.RuntimeInfo;
import com.solo.tekton.mq.consumer.handler.TaskFactory;
import com.solo.tekton.mq.consumer.service.LogService;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
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
        taskHandling(body);
    }

    @Async
    private void taskHandling(byte[] body) {
        ObjectMapper mapper = new ObjectMapper();
        RuntimeInfo runtimeInfo = null;
        try {
            runtimeInfo = mapper.readValue(body, RuntimeInfo.class);
        } catch (IOException e) {
            log.error("Task wasn't handled, due to failed to parse message: " + new String(body));
            return;
        }
        // Should not happen
        if (runtimeInfo == null) {
            log.error("Task wasn't handled, due to failed to parse message: " + new String(body));
            return;
        }
        BaseTask task = TaskFactory.createTask(runtimeInfo);
        TaskLog taskLog = this.generateTaskLog(runtimeInfo);
        Long taskInstanceId = taskLog.getTaskInstanceId();
        try {
            // Create pipelineRun
            task.createPipelineRun(kubernetesClient, namespace);
            rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
            MessagePostProcessor messagePostProcessor = new MessagePostProcessor() {
                @Override
                public Message postProcessMessage(Message message) throws AmqpException {
                    message.getMessageProperties().setContentType("application/json");
                    message.getMessageProperties().setContentEncoding("UTF-8");
                    return message;
                }
            };
            // Send message for redirect logs
            rabbitTemplate.convertAndSend(exchange, routingKey, taskLog, messagePostProcessor);
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
