package com.solo.tekton.mq.consumer.handler;

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

@RequiredArgsConstructor
@Slf4j
public class TaskGit implements BaseTask {

    @NonNull
    private RuntimeInfo runtimeInfo;

    private String basicAuthSecretName;

    @Override
    public boolean createPipelineRun(TektonClient tektonClient) {
        try {
            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                    .withGenerateName("task-git-")
                    .withNamespace("default")
                    .addToAnnotations("devops.flow/tenantId", "1234")
                    .addToAnnotations("devops.flow/systemId", "1234")
                    .addToAnnotations("devops.flow/flowId", "1234")
                    .addToAnnotations("devops.flow/flowInstanceId", "1234")
                    .addToAnnotations("devops.flow/taskInstanceId", "1234")
                    .endMetadata()
                    .withNewSpec()
                    .withNewPipelineRef()
                    .withName("task-git")
                    .endPipelineRef()
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("data")
                            .withNewPersistentVolumeClaim("test-01", false)
                            .build())
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("basic-auth")
                            .withSecret(new SecretVolumeSourceBuilder().withSecretName("my-basic-auth-secret").build())
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_INSTANCE_ID")
                            .withNewValue("123456")
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("REPO_URL")
                            .withNewValue("https://github.com/xsoloking/jenkins-shared-libraries.git")
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("REPO_REVISION")
                            .withNewValue("dev")
                            .build())
                    .withNewTimeouts()
                    .withPipeline(Duration.parse("40m"))
                    .withTasks(Duration.parse("30m"))
                    .withFinally(Duration.parse("5m"))
                    .endTimeouts()
                    .endSpec()
                    .build();
            tektonClient.v1().pipelineRuns().createOrReplace(pipelineRun);
        } catch (ParseException e) {
            log.error("Create pipelineRun with info {} was failed with an exception:", runtimeInfo, e);
            return false;
        }
        return true;
    }

    @Override
    public boolean prepareResources(KubernetesClient kubernetesClient) {
        System.out.println("Git setup");
        return true;
    }
}
