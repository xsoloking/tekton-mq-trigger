package com.solo.tekton.mq.consumer.handler;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RuntimeInfo {

    private String project;

    private String token;

    private List<Map<String, String>> parameters;

}
