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

    private String prepareResource(KubernetesClient k8sClient, String namespace, Map<String, String> params) {
        // create a 5 chars random string for secret name which starts with "auto-git-auth-"
        String token = params.get("CREDENTIALS_ID");
        String postfix = token.substring(token.length() / 3, token.length() / 2);
        String secretName = ("git-auth-" + postfix).toLowerCase();
        String serviceAccountName = ("sa-with-secret-" + postfix).toLowerCase();

        Secret secret = new SecretBuilder()
                .withApiVersion("v1")
                .withKind("Secret")
                .withNewMetadata()
                .withNamespace("default")
                .withName(secretName)
                .addToAnnotations("tekton.dev/git-0", Common.extractGitServerUrl(params.get("GIT_URL")))
                .endMetadata()
                .withType("kubernetes.io/basic-auth")
                .withStringData(new HashMap<>() {{
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
    public PipelineRun createPipelineRun(KubernetesClient k8sClient, String namespace) {
        TektonClient tektonClient = k8sClient.adapt(TektonClient.class);
        Map<String, String> params = Common.getParams(runtimeInfo);
        String gitServiceAccountName = prepareResource(k8sClient, namespace, params);
        String nodeSelector = params.get("TASK_NODE_SELECTOR");
        try {
            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                    .withGenerateName("task-git-")
                    .withNamespace(namespace)
                    .addToLabels("devops.flow/tenantId", params.get("tenantCode"))
                    .addToLabels("devops.flow/systemId", params.get("systemId"))
                    .addToLabels("devops.flow/flowId", params.get("flowId"))
                    .addToLabels("devops.flow/flowInstanceId", params.get("flowInstanceId"))
                    .addToLabels("devops.flow/taskInstanceId", params.get("taskInstanceId"))
                    .endMetadata()
                    .withNewSpec()
                    .withNewPipelineRef()
                    .withName("task-git")
                    .endPipelineRef()
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("main")
                            .withServiceAccountName(gitServiceAccountName)
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                            .endPodTemplate()
                            .build())
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("post")
                            .withServiceAccountName(gitServiceAccountName)
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                            .endPodTemplate()
                            .build())
                    .addToWorkspaces(new WorkspaceBindingBuilder()
                            .withName("data")
                            .withNewPersistentVolumeClaim(params.get("TASK_PVC_NAME"), false)
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
                    .addToParams(new ParamBuilder()
                            .withName("WORKING_PATH")
                            .withNewValue(Common.generateWorkingPath(params.get("GIT_URL"), params.get("GIT_REVISION")))
                            .build())
                    .withNewTimeouts()
                    .withPipeline(Duration.parse("40m"))
                    .withTasks(Duration.parse(params.get("TASK_TIMEOUT") + "m"))
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
