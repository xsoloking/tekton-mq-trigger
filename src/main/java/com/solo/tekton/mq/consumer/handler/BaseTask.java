package com.solo.tekton.mq.consumer.handler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.pipeline.v1.PipelineRun;

public interface BaseTask {
    PipelineRun createPipelineRun(KubernetesClient k8sClient, String namespace);
}
