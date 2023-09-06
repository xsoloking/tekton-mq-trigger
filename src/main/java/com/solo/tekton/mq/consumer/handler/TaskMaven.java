package com.solo.tekton.mq.consumer.handler;

import com.solo.tekton.mq.consumer.utils.Common;
import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class TaskMaven implements BaseTask {

    @NonNull
    private RuntimeInfo runtimeInfo;

    @Override
    public PipelineRun createPipelineRun(KubernetesClient k8sClient, String namespace) {
        TektonClient tektonClient = k8sClient.adapt(TektonClient.class);
        Map<String, String> params = Common.getParams(runtimeInfo);
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
                                    .withNewHostPath("/root/cache/maven", "DirectoryOrCreate")
                                    .build())
                            .endPodTemplate()
                            .build())
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("post")
                            // TODO load the value of service account from configuration
                            .withServiceAccountName("git-basic-auth-4-post-task")
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
