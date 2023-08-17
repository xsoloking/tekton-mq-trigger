package com.solo.tekton.mq.consumer.handler;

import com.solo.tekton.mq.consumer.utils.Common;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class TaskGit implements BaseTask {

    @NonNull
    private RuntimeInfo runtimeInfo;

    private String prepareResource(KubernetesClient k8sClient, Map<String, String> params) {
        // create a 5 chars random string for secret name which starts with "auto-git-auth-"
        String token = params.get("CREDENTIALS_ID");
        String postfix = token.substring(token.length() / 3, token.length() / 2);
        String secretName = "git-auth-" + postfix;
        String serviceAccountName = "sa-with-secret-" + postfix;

        Secret secret = new SecretBuilder()
                .withApiVersion("v1")
                .withKind("Secret")
                .withNewMetadata()
                .withNamespace("default")
                .withName(secretName)
                .addToAnnotations("tekton.dev/git-0", Common.extractGitServerUrl(params.get("GIT_URL")))
                .endMetadata()
                .withType("kubernetes.io/basic-auth")
                .withStringData(new HashMap<String, String>() {{
                    put("username", "git");
                    put("password", new String(Base64.getDecoder().decode(token)));
                }})
                .build();
        k8sClient.secrets().resource(secret).serverSideApply();

        ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withApiVersion("v1")
                .withKind("ServiceAccount")
                .withNewMetadata()
                .withNamespace("default")
                .withName(serviceAccountName)
                .endMetadata()
                .withSecrets()
                .addToSecrets(new ObjectReferenceBuilder()
                        .withKind("Secret")
                        .withName(secretName)
                        .withApiVersion("v1")
                        .build())
                .build();
        k8sClient.serviceAccounts().resource(serviceAccount).serverSideApply();

        return serviceAccountName;
    }

    @Override
    public boolean createPipelineRun(KubernetesClient k8sClient) {
        TektonClient tektonClient = k8sClient.adapt(TektonClient.class);
        Map<String, String> params = Common.getParams(runtimeInfo);
        String nodeSelector = params.get("TASK_NODE_SELECTOR");
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
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("core-task")
                            .withServiceAccountName(prepareResource(k8sClient, params))
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1])
                            .endPodTemplate()
                            .build())
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
