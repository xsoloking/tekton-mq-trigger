package com.solo.tekton.mq.consumer.utils;

import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.data.TaskLog;

import java.net.URI;
import java.util.Map;

public class Common {

    public static String extractServerUrl(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            return String.format("%s://%s", uri.getScheme(), host);
        }
        return String.format("%s://%s:%d", uri.getScheme(), host, port);
    }

    public static String extractServerHost(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            return uri.getHost();
        }
        return String.format("%s:%d", host, port);
    }

    public static String generateWorkingPath(String repoUrl, String revision) {
        URI uri = URI.create(repoUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        String serverUrl = String.format("%s://%s:%d/", uri.getScheme(), host, port);
        if (port == -1) {
            serverUrl = String.format("%s://%s/", uri.getScheme(), host);
        }
        return repoUrl.replace(serverUrl, "").replace(".git", "/" + revision);
    }

    public static TaskLog generateTaskLog(RuntimeInfo runtimeInfo) {
        TaskLog taskLog = new TaskLog();
        Map<String, String> params = runtimeInfo.getParams();
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
