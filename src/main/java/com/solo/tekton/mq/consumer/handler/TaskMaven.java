package com.solo.tekton.mq.consumer.handler;

import com.solo.tekton.mq.consumer.utils.Common;
import io.fabric8.kubernetes.api.model.Duration;
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
    public boolean createPipelineRun(KubernetesClient k8sClient) {
        TektonClient tektonClient = k8sClient.adapt(TektonClient.class);
        Map<String, String> params = Common.getParams(runtimeInfo);
        String nodeSelector = params.get("TASK_NODE_SELECTOR");
        try {
            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                    .withGenerateName("task-maven-")
                    .withNamespace("default")
                    .addToAnnotations("devops.flow/tenantId", params.get("tenantCode"))
                    .addToAnnotations("devops.flow/systemId", params.get("systemId"))
                    .addToAnnotations("devops.flow/flowId", params.get("flowId"))
                    .addToAnnotations("devops.flow/flowInstanceId", params.get("flowInstanceId"))
                    .addToAnnotations("devops.flow/taskInstanceId", params.get("taskInstanceId"))
                    .endMetadata()
                    .withNewSpec()
                    .withNewPipelineRef()
                    .withName("task-maven")
                    .endPipelineRef()
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("main")
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                            .endPodTemplate()
                            .build())
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("post")
                            .withServiceAccountName("git-basic-auth-4-post-task")
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                            .endPodTemplate()
                            .build())
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("data")
                            .withNewPersistentVolumeClaim(params.get("TASK_PVC_NAME"), false)
                            .build())
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("cache")
                            .withNewPersistentVolumeClaim(params.get("TASK_CACHE_PVC_NAME"), false)
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_INSTANCE_ID")
                            .withNewValue(params.get("taskInstanceId"))
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
                    .withTasks(Duration.parse(params.get("TASK_TIMEOUT") + "m"))
                    .withFinally(Duration.parse("2m"))
                    .endTimeouts()
                    .endSpec()
                    .build();
            Object results = tektonClient.v1().pipelineRuns().resource(pipelineRun).create();
            log.info("Create pipelineRun with info {} was successful with results: {}", runtimeInfo, results);
        } catch (ParseException e) {
            log.error("Create pipelineRun with info {} was failed with an exception:", runtimeInfo, e);
            return false;
        }
        return true;
    }
}
