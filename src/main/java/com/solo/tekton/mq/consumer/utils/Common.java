package com.solo.tekton.mq.consumer.utils;

import com.solo.tekton.mq.consumer.handler.RuntimeInfo;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class Common {

    public static Map<String, String> getParams(RuntimeInfo runtimeInfo) {
        Map<String, String> params = new HashMap<>();
        runtimeInfo.getParameters().stream()
                .forEach(item -> params.put(item.get("name"), item.get("value")));
        return params;
    }

    public static String extractGitServerUrl(String gitUrl) {
        URI uri = URI.create(gitUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = 80;
        }
        return String.format("%s://%s:%d", uri.getScheme(), host, port);
    }
}
