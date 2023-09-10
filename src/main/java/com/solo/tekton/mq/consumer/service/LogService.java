package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.TaskLog;
import com.solo.tekton.mq.consumer.handler.RuntimeInfo;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.solo.tekton.mq.consumer.utils.Common.getParams;

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
        try {
            final CountDownLatch watchLatch = new CountDownLatch(1);
            PodResource mainTaskPod = kubernetesClient.pods().inNamespace(namespace).withName(taskLog.getPodName());
            try(Watch ignored = mainTaskPod.watch(new Watcher<Pod>() {
                            @Override
                            public void eventReceived(Action action, Pod resource) {
                                if (resource.getStatus().getPhase().equals("Running")) {
                                    log.info("The pod \"{}\" is running", taskLog.getPodName());
                                    watchLatch.countDown();
                                } else {
                                    log.info("Waiting for pod \"{}\" to be ready ...", taskLog.getPodName());
                                }
                            }

                            @Override
                            public void onClose(WatcherException cause) {

                            }
                        })) {
                boolean ready = watchLatch.await(120, TimeUnit.SECONDS);
                if (!ready) {
                    throw new RuntimeException("Timed out waiting for pod " + taskLog.getPodName() + " to start");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Unexpected interrupted exception: {}, stack trace: {}", e.getMessage(), e.getStackTrace());
                throw new RuntimeException(e);
            }
            log.info("Started to redirect logs for pod  \"{}\"", taskLog.getPodName());
            LogWatch watch = mainTaskPod.inContainer("step-executor").watchLog(new OutputStream() {
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
            mainTaskPod.waitUntilCondition(r -> r.getStatus().getPhase().equals("Succeeded")
                    || r.getStatus().getPhase().equals("Failed"), taskLog.getTimeout(), TimeUnit.MINUTES);
            log.info("Finished to redirect logs for pod  \"{}\"", taskLog.getPodName());
        } catch (Exception e) {
            taskLog.setLogContent("An exception happened during log redirection: " + e.getMessage());
            insertLogToMongo(taskLog);
            log.error("Redirecting logs for task \"{}\" was failed due to {}", taskLog.getTaskInstanceId(), e.getMessage());
        }
    }


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

    public void insertLogToMongo(RuntimeInfo info, String content) {
        TaskLog taskLog = generateTaskLog(info);
        taskLog.setLogContent(content);
        insertLogToMongo(taskLog);
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
}
