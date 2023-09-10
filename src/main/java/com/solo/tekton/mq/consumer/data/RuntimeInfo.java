package com.solo.tekton.mq.consumer.data;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class RuntimeInfo {

    private String project;

    private String token;

    private List<Map<String, String>> parameter;

    public Map<String, String> getParams() {
        Map<String, String> params = new HashMap<>();
        this.getParameter()
                .forEach(item -> params.put(item.get("name"), item.get("value")));
        return params;
    }
}
