package com.solo.tekton.mq.consumer.handler;

import com.solo.tekton.mq.consumer.utils.Common;
import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.ParamBuilder;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunBuilder;
import io.fabric8.tekton.pipeline.v1.WorkspaceBindingBuilder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class TaskGit implements BaseTask {

    @NonNull
    private RuntimeInfo runtimeInfo;

    private String basicAuthSecretName;

    @Override
    public boolean createPipelineRun(KubernetesClient k8sClient) {
        TektonClient tektonClient = k8sClient.adapt(TektonClient.class);
        Map<String, String> params = Common.getParams(runtimeInfo);
        try {
            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                    .withGenerateName("task-git-")
                    .withNamespace("default")
                    .addToAnnotations("devops.flow/tenantId", params.get("tenantCode"))
                    .addToAnnotations("devops.flow/systemId", params.get("systemId"))
                    .addToAnnotations("devops.flow/flowId", params.get("flowId"))
                    .addToAnnotations("devops.flow/flowInstanceId", params.get("flowInstanceId"))
                    .addToAnnotations("devops.flow/taskInstanceId", params.get("taskInstanceId"))
                    .endMetadata()
                    .withNewSpec()
                    .withNewPipelineRef()
                    .withName("task-git")
                    .endPipelineRef()
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("data")
                            .withNewPersistentVolumeClaim(params.get("TASK_PVC_NAME"), false)
                            .build())
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("basic-auth")
                            .withSecret(new SecretVolumeSourceBuilder().withSecretName("my-basic-auth-secret").build())
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_INSTANCE_ID")
                            .withNewValue(params.get("taskInstanceId"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("REPO_URL")
                            .withNewValue(params.get("GIT_URL"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("REPO_REVISION")
                            .withNewValue(params.get("GIT_REVISION"))
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
