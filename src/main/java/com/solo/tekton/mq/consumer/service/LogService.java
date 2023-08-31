package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.TaskLog;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LogService {

    @Autowired
    KubernetesClient kubernetesClient;

    @Autowired
    MongoTemplate mongoTemplate;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    @Async
    public void redirectLogs(TaskLog taskLog, long timeout) {
        log.info("Redirecting logs for taskInstance: " + taskLog.getTaskInstanceId());

        try {
            Map<String, String> labelFilter = Map.of(
                    "devops.flow/taskInstanceId",
                    String.valueOf(taskLog.getTaskInstanceId()),
                    "tekton.dev/pipelineTask","main");
            List<Pod> pods = kubernetesClient.pods().inNamespace("default").withLabels(labelFilter).list().getItems();
            if (pods.isEmpty()) {
                log.info("Log redirection was cancelled , no pod found for taskInstance: " + taskLog.getTaskInstanceId());
                return;
            }

            LogWatch watch= kubernetesClient.pods().inNamespace(namespace).withName(podName).watchLog(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    log.info("Pod {} logs -> not used", podName);
                }
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    for (String line: new String(b, off, len).trim().split("\n")) {
                        log.info("Pod {} logs -> {}", podName, line);
                        TaskLog taskLog = new TaskLog();
                        taskLog.setFlowInstanceId(123L);
                        taskLog.setNodeInstanceId(123L);
                        taskLog.setTaskInstanceId(123L);
                        taskLog.setExecuteBatchId(123L);
                        taskLog.setLogContent(line);
                        taskLog.setHtmlLog(false);
                        taskLog.setLogType(1);
                        mongoTemplate.insert(taskLog, "987654321");
                    }
                }
            });
            kubernetesClient.pods().inNamespace(namespace).withName(podName).waitUntilCondition(  r -> r.getStatus().getPhase().equals("Succeeded") || r.getStatus().getPhase().equals("Succeeded"), timeout, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void insertLogToMongo(TaskLog taskLog) {
        String timestamp = sdf.format(new Timestamp(System.currentTimeMillis()));
        String msg = timestamp + " [INFO] " + taskLog.getLogContent();
        taskLog.setLogType(2);
        if(taskLog.getLogContent().contains("ERROR")) {
            taskLog.setLogType(1);
            msg = timestamp + " [ERROR] " + taskLog.getLogContent();
        } else if (taskLog.getLogContent().contains("WARNING")) {
            taskLog.setLogType(3);
            msg = timestamp + " [WARNING] " + taskLog.getLogContent();
        }
        taskLog.setLogContent(msg);
        mongoTemplate.insert(taskLog, String.valueOf(taskLog.getTaskInstanceId()));
    }
}
