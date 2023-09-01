package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.TaskLog;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LogService {

    @Autowired
    KubernetesClient kubernetesClient;

    @Autowired
    MongoTemplate mongoTemplate;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Redirects logs for a task.
     *
     * @param taskLog the TaskLog object containing the task instance ID
     * @return void
     */
    @Async
    public void redirectLogs(TaskLog taskLog) {
        log.info("Redirecting logs for task \"{}\"", taskLog.getTaskInstanceId());
        try {
            Map<String, String> labelFilter = Map.of(
                    "devops.flow/taskInstanceId", String.valueOf(taskLog.getTaskInstanceId()),
                    "tekton.dev/pipelineTask", "main");
            Optional<PodResource> podRes = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabels(labelFilter)
                    .resources()
                    .findFirst();
            if (podRes.isEmpty()) {
                taskLog.setLogContent("ERROR::no pod found for taskInstance: " + taskLog.getTaskInstanceId());
                insertLogToMongo(taskLog);
                log.info("no pod found for taskInstance: {}, log redirection was cancelled", taskLog.getTaskInstanceId());
                return;
            }
//            Object result = podRes.get().waitUntilCondition(  r -> r.getStatus().getPhase().equals("Running"), 5, TimeUnit.MINUTES);
//            if ( result == null || result.equals(false)) {
//                log.info("Task \"{}\" doesn't start to run in {} minutes, log redirection was cancelled", taskLog.getTaskInstanceId());
//                return;
//            }
            LogWatch watch = podRes.get().watchLog(new OutputStream() {
                @Override
                public void write(int b) {
                    throw new RuntimeException("not used");
                }

                @Override
                public void write(byte @NonNull [] b, int off, int len) {
                    for (String line : new String(b, off, len).trim().split("\n")) {
                        taskLog.setLogContent(line);
                        insertLogToMongo(taskLog);
                    }
                }
            });
            podRes.get().waitUntilCondition(r -> r.getStatus().getPhase().equals("Succeeded")
                    || r.getStatus().getPhase().equals("Terminated"), taskLog.getTimeout(), TimeUnit.MINUTES);
        } catch (Exception e) {
            taskLog.setLogContent("An exception happened during log redirection: " + e.getMessage());
            insertLogToMongo(taskLog);
            log.error("Redirecting logs for task \"{}\" was failed due to {}", taskLog.getTaskInstanceId(), e.getMessage());
        }
    }

    @Async
    public void insertLogToMongo(TaskLog taskLog) {
        TaskLog newTaskLog = new TaskLog();
        newTaskLog.setTaskInstanceId(taskLog.getTaskInstanceId());
        newTaskLog.setExecuteBatchId(taskLog.getExecuteBatchId());
        newTaskLog.setFlowInstanceId(taskLog.getFlowInstanceId());
        newTaskLog.setNodeInstanceId(taskLog.getNodeInstanceId());
        String timestamp = sdf.format(new Timestamp(System.currentTimeMillis()));
        String msg = timestamp + " [INFO] " + taskLog.getLogContent();
        newTaskLog.setLogType(2);
        if (taskLog.getLogContent().contains("ERROR") || taskLog.getLogContent().toLowerCase().contains("exception")) {
            newTaskLog.setLogType(1);
            msg = timestamp + " [ERROR] " + taskLog.getLogContent();
        } else if (taskLog.getLogContent().toLowerCase().contains("warning")) {
            newTaskLog.setLogType(3);
            msg = timestamp + " [WARNING] " + taskLog.getLogContent();
        }
        if (taskLog.getLogContent().toLowerCase().contains("http://") || taskLog.getLogContent().toLowerCase().contains("https://")) {
            newTaskLog.setHtmlLog(true);
        }
        newTaskLog.setLogContent(msg);
        mongoTemplate.insert(newTaskLog, String.valueOf(taskLog.getTaskInstanceId()));
    }
}
