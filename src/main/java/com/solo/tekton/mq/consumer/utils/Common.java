package com.solo.tekton.mq.consumer.utils;

import com.solo.tekton.mq.consumer.handler.RuntimeInfo;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class Common {

    public static Map<String, String> getParams(RuntimeInfo runtimeInfo) {
        Map<String, String> params = new HashMap<>();
        runtimeInfo.getParameters()
                .forEach(item -> params.put(item.get("name"), item.get("value")));
        return params;
    }

    public static String extractGitServerUrl(String repoUrl) {
        URI uri = URI.create(repoUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            return String.format("%s://%s", uri.getScheme(), host);
        }
        return String.format("%s://%s:%d", uri.getScheme(), host, port);
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
}
