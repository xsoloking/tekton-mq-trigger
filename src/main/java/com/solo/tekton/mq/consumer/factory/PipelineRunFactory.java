package com.solo.tekton.mq.consumer.factory;

import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.service.PipelineRunService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PipelineRunFactory {

    @Autowired
    private List<PipelineRunService> services;

    private static final Map<String, PipelineRunService> pipelineRunServiceFactory = new HashMap<>();

    @PostConstruct
    public void initPipelineRunFactory() {
        for(PipelineRunService service : services) {
            pipelineRunServiceFactory.put(service.getType(), service);
        }
    }

    public static PipelineRunService getPipelineRunService(RuntimeInfo runtimeInfo) {
        PipelineRunService service = pipelineRunServiceFactory.get(runtimeInfo.getProject());
        if(service == null) throw new RuntimeException("Unknown task type: " + runtimeInfo.getProject());
        return service;
    }

}
