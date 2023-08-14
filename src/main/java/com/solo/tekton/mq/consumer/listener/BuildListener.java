package com.solo.tekton.mq.consumer.listener;

import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.tekton.client.TektonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.fabric8.tekton.pipeline.v1.*;

import java.text.ParseException;


@Component
@Slf4j
public class BuildListener {

    @Autowired
    TektonClient tektonClient;

    @RabbitListener(queues = "tasks-triggered")
    public void receiveMessage(byte[] body) throws ParseException {
        log.info("Received message: " + new String(body));
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
        // Create TaskRun
        tektonClient.v1().pipelineRuns().createOrReplace(pipelineRun);
    }

}
