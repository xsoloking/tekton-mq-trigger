package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import io.fabric8.tekton.pipeline.v1.PipelineRun;

public interface PipelineRunService {

    String getType();

    PipelineRun createPipelineRun(RuntimeInfo runtimeInfo);
}
