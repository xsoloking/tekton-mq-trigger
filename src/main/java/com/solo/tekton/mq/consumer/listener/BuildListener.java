package com.solo.tekton.mq.consumer.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.TaskLog;
import com.solo.tekton.mq.consumer.handler.BaseTask;
import com.solo.tekton.mq.consumer.handler.RuntimeInfo;
import com.solo.tekton.mq.consumer.handler.TaskFactory;
import com.solo.tekton.mq.consumer.service.LogService;
import io.fabric8.knative.internal.pkg.apis.Condition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    String loggingRoutingKey;

    @Value("${flow.mq.routing.key.end}")
    String endRoutingKey;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    MessagePostProcessor messagePostProcessor;

    @Autowired
    LogService logService;

    @RabbitListener(queues = "${flow.mq.queue.triggered}")
    public void receiveMessage(byte[] body) throws IOException {
        log.debug("BuildListener::Received message: " + new String(body));
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
        try {
            // Create pipelineRun
            PipelineRun pipelineRun = task.createPipelineRun(kubernetesClient, namespace);
            log.debug("Created pipelineRun for task \"{}\": {}", runtimeInfo.getProject(), pipelineRun);
            TaskLog taskLog = this.generateTaskLog(runtimeInfo);
            taskLog.setLogContent("Created pipelineRun \"" + pipelineRun.getMetadata().getName() + "\"");
            logService.insertLogToMongo(taskLog);
            postPipelineRun(pipelineRun, runtimeInfo);
        } catch (RuntimeException e) {
            TaskLog taskLog = this.generateTaskLog(runtimeInfo);
            Long taskInstanceId = taskLog.getTaskInstanceId();
            log.error("Create pipelineRun for task \"{}:{}\"  was failed with an exception: {}",
                    runtimeInfo.getProject(), taskInstanceId, e.getMessage());
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
        taskLog.setTimeout(30L);
        if (params.containsKey("TASK_TIMEOUT") && params.get("TASK_TIMEOUT") != null) {
            taskLog.setTimeout(Long.parseLong(params.get("TASK_TIMEOUT")));
        }
        return taskLog;
    }

    private void postPipelineRun(PipelineRun pipelineRun, RuntimeInfo runtimeInfo) {
        TektonClient tektonClient = kubernetesClient.adapt(TektonClient.class);
        final CountDownLatch watchLatch = new CountDownLatch(1);
        try (Watch ignored = tektonClient.v1().pipelineRuns().inNamespace(namespace).withName(pipelineRun.getMetadata().getName()).watch(
                new Watcher<PipelineRun>() {
                    @Override
                    public void eventReceived(Action action, PipelineRun resource) {
                        Condition condition = null;
                        try {
                            condition = resource.getStatus().getConditions().stream().findFirst().orElse(null);
                        } catch (Exception e) {
                            log.debug("Failed to get condition for PipelineRun \"{}\": {}", pipelineRun.getMetadata().getName(),resource);
                            return;
                        }
                        if (condition == null) {
                            return;
                        }
                        if (condition.getReason().equals("Running")) {
                            TaskLog taskLog = generateTaskLog(runtimeInfo);
                            taskLog.setPodName(pipelineRun.getMetadata().getName() + "-main-pod");
                            rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
                            // Send message for redirect logs
                            rabbitTemplate.convertAndSend(exchange, loggingRoutingKey, taskLog, messagePostProcessor);
                            log.info("PipelineRun \"{}\" for task \"{}:{}\" is running", pipelineRun.getMetadata().getName(), runtimeInfo.getProject(), taskLog.getTaskInstanceId());
                            taskLog.setLogContent("PipelineRun \"" + pipelineRun.getMetadata().getName() + "\" is running. Wait for the pod to start ...");
                            logService.insertLogToMongo(taskLog);
                            watchLatch.countDown();
                        } else if (condition.getReason().equals("CouldntGetPipeline")) {
                            TaskLog taskLog = generateTaskLog(runtimeInfo);
                            log.info("Failed to run pipeline \"{}\" due to: {}", pipelineRun.getMetadata().getName(), condition.getMessage());
                            taskLog.setLogContent("Failed to run pipeline \"" + pipelineRun.getMetadata().getName() + "\" due to: " + condition.getMessage());
                            logService.insertLogToMongo(taskLog);
                            // Send message to stop task execution
                            JSONObject result = new JSONObject();
                            try {
                                result.put("taskInstanceId", String.valueOf(taskLog.getTaskInstanceId()));
                                result.put("status", "FAILURE");
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            rabbitTemplate.convertAndSend(exchange, endRoutingKey, result.toString(), messagePostProcessor);
                            watchLatch.countDown();
                        } else {
                            log.info("The status of PipelineRun \"{}\": Condition \"{}\"", pipelineRun.getMetadata().getName(), condition);
                        }
                    }

                    @Override
                    public void onClose(WatcherException cause) {

                    }
                })) {
            watchLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Could not watch PipelineRun \"{}\" due to exception: {}", pipelineRun.getMetadata().getName(), interruptedException.getMessage());
        }
    }
}
