package com.solo.tekton.mq.consumer.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PipelineRunGradleService extends PipelineRunMavenService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Gradle";

    public final String PIPELINE_RUN_GENERATE_NAME = "task-gradle-";

    public final String REF_PIPELINE_NAME = "task-gradle";

    @Override
    public String getType() {
        return TYPE;
    }

    public String cachePath = "/data/cache/gradle/caches";

    public final String cacheType = "gradle";
}
