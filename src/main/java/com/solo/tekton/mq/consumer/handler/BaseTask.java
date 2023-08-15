package com.solo.tekton.mq.consumer.handler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.PipelineRun;

import java.util.List;

public interface BaseTask {
    boolean createPipelineRun(KubernetesClient k8sClient);
}
