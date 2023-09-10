package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Map;

@Service
@Slf4j
public class PipelineRunMavenService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Maven";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PipelineRun createPipelineRun(RuntimeInfo runtimeInfo) {
        Map<String, String> params = runtimeInfo.getParams();
        String nodeSelector = "kubernetes.io/os: \"linux\"";
        if (params.containsKey("TASK_NODE_SELECTOR") && params.get("TASK_NODE_SELECTOR") != null) {
            nodeSelector = params.get("TASK_NODE_SELECTOR");
        }
        String timeout = "30m";
        if (params.containsKey("TASK_TIMEOUT") && params.get("TASK_TIMEOUT") != null) {
            timeout = params.get("TASK_TIMEOUT") + "m";
        }
        try {
            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                    .withGenerateName("task-maven-")
                    .withNamespace(namespace)
                    .addToLabels("devops.flow/tenantId", params.get("tenantCode"))
                    .addToLabels("devops.flow/systemId", params.get("systemId"))
                    .addToLabels("devops.flow/flowId", params.get("flowId"))
                    .addToLabels("devops.flow/flowInstanceId", params.get("flowInstanceId"))
                    .addToLabels("devops.flow/taskInstanceId", params.get("taskInstanceId"))
                    .endMetadata()
                    .withNewSpec()
                    .withNewPipelineRef()
                    .withName("task-maven")
                    .endPipelineRef()
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("main")
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                            .addToVolumes(new VolumeBuilder()
                                    .withName("maven-cache")
                                    .withNewHostPath("/data/cache/maven/repository", "DirectoryOrCreate")
                                    .build())
                            .endPodTemplate()
                            .build())
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("post")
                            // TODO load the value of service account from configuration
                            .withServiceAccountName(serviceAccountForPostTask)
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                            .endPodTemplate()
                            .build())
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("data")
                            .withNewPersistentVolumeClaim(params.get("TASK_PVC_NAME"), false)
                            .build())
//                    .addToWorkspaces(new WorkspaceBindingBuilder()
//                            .withName("cache")
//                            .withNewPersistentVolumeClaim(params.get("TASK_CACHE_PVC_NAME"), false)
//                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_INSTANCE_ID")
                            .withNewValue(params.get("taskInstanceId"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("FLOW_INSTANCE_ID")
                            .withNewValue(params.get("flowInstanceId"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_IMAGE")
                            .withNewValue(params.get("TASK_IMAGE"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_SCRIPT")
                            .withNewValue(params.get("SCRIPT"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("WORKING_PATH")
                            .withNewValue(params.get("SOURCE"))
                            .build())
                    .withNewTimeouts()
                    .withPipeline(Duration.parse("40m"))
                    .withTasks(Duration.parse(timeout))
                    .withFinally(Duration.parse("2m"))
                    .endTimeouts()
                    .endSpec()
                    .build();
            return tektonClient.v1().pipelineRuns().resource(pipelineRun).create();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
