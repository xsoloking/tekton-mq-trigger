package com.solo.tekton.mq.consumer.utils;

import com.solo.tekton.mq.consumer.handler.RuntimeInfo;

import java.util.HashMap;
import java.util.Map;

public class Common {

    public static Map<String, String> getParams(RuntimeInfo runtimeInfo) {
        Map<String, String> params = new HashMap<>();
        runtimeInfo.getParameters().stream()
                .forEach(item -> params.put(item.get("name"), item.get("value")));
        return params;
    }
}
