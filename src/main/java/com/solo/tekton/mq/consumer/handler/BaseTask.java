package com.solo.tekton.mq.consumer.handler;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface BaseTask {
    void createPipelineRun(KubernetesClient k8sClient, String namespace);
}
