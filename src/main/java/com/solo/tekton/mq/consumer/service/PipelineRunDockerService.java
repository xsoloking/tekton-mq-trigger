package com.solo.tekton.mq.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.DockerBuildData;
import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.utils.Common;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PipelineRunDockerService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Docker";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PipelineRun createPipelineRun(RuntimeInfo runtimeInfo) {
        Map<String, String> params = runtimeInfo.getParams();
        DockerBuildData[] buildData = null;
        try {
            String json = new String(Base64.getDecoder().decode(params.get("DOCKER_BUILD_DATA")));
            buildData = new ObjectMapper().readValue(json, DockerBuildData[].class);
        } catch (IOException e) {
            log.error("The value of the parameter \"DOCKER_BUILD_DATA\" is invalid, the task will be skipped due to exception: {}", e);
        }

        if (buildData == null || buildData.length == 0) {
            log.error("The parameter \"DOCKER_BUILD_DATA\" is invalid, the task will be skipped: {}", runtimeInfo);
            throw new RuntimeException("The parameter \"DOCKER_BUILD_DATA\" is empty");
        }
        String serviceAccountName = "default";
        String nodeSelector = "kubernetes.io/os: \"linux\"";
        if (params.containsKey("TASK_NODE_SELECTOR") && params.get("TASK_NODE_SELECTOR") != null) {
            nodeSelector = params.get("TASK_NODE_SELECTOR");
        }
        String timeout = "30m";
        if (params.containsKey("TASK_TIMEOUT") && params.get("TASK_TIMEOUT") != null) {
            timeout = params.get("TASK_TIMEOUT") + "m";
        }
        String dingExtraArgs = "";
        String output = "\"type=docker,dest=- . > image.tar\"";
        if (buildData[0].getRepository() != null && !buildData[0].getRepository().isEmpty()) {
            output = "type=registry,push=true,registry.insecure=true";
            serviceAccountName = prepareResource(buildData[0]);
            dingExtraArgs = "--insecure-registry=" + Common.extractServerHost(buildData[0].getRepository());
        }
        StringBuilder buildExtraArgs = new StringBuilder();
        for (String tag : buildData[0].getTags()) {
            buildExtraArgs.append("--tag ").append(tag).append(" ");
        }
        try {
            PipelineRun pipelineRun = new PipelineRunBuilder()
                    .withNewMetadata()
                    .withGenerateName("task-docker-")
                    .withNamespace(namespace)
                    .addToLabels("devops.flow/tenantId", params.get("tenantCode"))
                    .addToLabels("devops.flow/systemId", params.get("systemId"))
                    .addToLabels("devops.flow/flowId", params.get("flowId"))
                    .addToLabels("devops.flow/flowInstanceId", params.get("flowInstanceId"))
                    .addToLabels("devops.flow/taskInstanceId", params.get("taskInstanceId"))
                    .endMetadata()
                    .withNewSpec()
                    .withNewPipelineRef()
                    .withName("task-docker")
                    .endPipelineRef()
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("main")
                            .withServiceAccountName(serviceAccountName)
                            .withNewPodTemplate()
                            .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                            .endPodTemplate()
                            .build())
                    .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                            .withPipelineTaskName("post")
                            .withServiceAccountName(serviceAccountForPostTask)
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
                            .withName("FLOW_INSTANCE_ID")
                            .withNewValue(params.get("flowInstanceId"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_IMAGE")
                            .withNewValue(params.get("TASK_IMAGE"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("WORKING_PATH")
                            .withNewValue(params.get("SOURCE"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("DOCKERFILE")
                            .withNewValue(buildData[0].getDockerfile())
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("CONTENT")
                            .withNewValue(buildData[0].getContext())
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("OUTPUT")
                            .withNewValue(output)
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("PLATFORM")
                            .withNewValue(buildData[0].getPlatform())
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("BUILD_EXTRA_ARGS")
                            .withNewValue(buildExtraArgs.toString())
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("DIND_EXTRA_ARGS")
                            .withNewValue(dingExtraArgs)
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

    private String prepareResource(DockerBuildData data) {
        // create a 5 chars random string for secret name which starts with "auto-git-auth-"
        String secretName = ("docker-auth-" + data.getUsername()).toLowerCase();
        String serviceAccountName = ("sa-with-secret-" + data.getUsername()).toLowerCase();

        Secret secret = new SecretBuilder()
                .withApiVersion("v1")
                .withKind("Secret")
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(secretName)
                .addToAnnotations("tekton.dev/docker-0", data.getRepository())
                .endMetadata()
                .withType("kubernetes.io/basic-auth")
                .withStringData(new HashMap<>() {{
                    put("username", data.getUsername());
                    put("password", new String(Base64.getDecoder().decode(data.getPassword())));
                }})
                .build();
        kubernetesClient.secrets().resource(secret).serverSideApply();

        ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withApiVersion("v1")
                .withKind("ServiceAccount")
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(serviceAccountName)
                .endMetadata()
                .withSecrets()
                .addToSecrets(new ObjectReferenceBuilder()
                        .withKind("Secret")
                        .withName(secretName)
                        .withApiVersion("v1")
                        .build())
                .build();
        kubernetesClient.serviceAccounts().resource(serviceAccount).serverSideApply();

        return serviceAccountName;
    }

}
