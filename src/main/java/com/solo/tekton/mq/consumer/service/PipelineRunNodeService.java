package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.utils.TektonResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Map;

@Service
@Slf4j
public class PipelineRunNodeService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Nodejs";

    public final String PIPELINE_RUN_GENERATE_NAME = "task-node-";

    public final String REF_PIPELINE_NAME = "task-node";

    @Override
    public String getType() {
        return TYPE;
    }

    public String cachePath = "/data/cache/npm";

    public final String cacheType = "npm";

    @Override
    public PipelineRun createPipelineRun(RuntimeInfo runtimeInfo) {
        Map<String, String> params = runtimeInfo.getParams();
        if (params.get("TASK_CACHE_PVC_NAME") != null && !params.get("TASK_CACHE_PVC_NAME").isEmpty() ) {
            String cachePvcName = params.get("TASK_CACHE_PVC_NAME");
            cachePath = "/data/cache/" + params.get("systemId") + "/" + cacheType + "/" + cachePvcName;
            if (cachePvcName.contains("platform")) {
                cachePath = "/data/cache/platform/" + cacheType + "/" + cachePvcName;
            }
        }
        try {
            PipelineRunBuilder pipelineRunBuilder = TektonResourceBuilder.createPipelineRunBuilderForShellWithCache(
                    runtimeInfo, namespace, PIPELINE_RUN_GENERATE_NAME, REF_PIPELINE_NAME, cachePath);
            pipelineRunBuilder.editSpec()
                    .editLastTaskRunSpec()
                    .withServiceAccountName(serviceAccountForPostTask)
                    .endTaskRunSpec()
                    .endSpec();
            return tektonClient.v1().pipelineRuns().resource(pipelineRunBuilder.build()).create();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
