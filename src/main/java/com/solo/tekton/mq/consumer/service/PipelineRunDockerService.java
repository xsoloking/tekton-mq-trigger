package com.solo.tekton.mq.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.DockerBuildData;
import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.utils.Common;
import com.solo.tekton.mq.consumer.utils.K8sResourceBuilder;
import com.solo.tekton.mq.consumer.utils.TektonResourceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.ParamBuilder;
import io.fabric8.tekton.pipeline.v1.PipelineRun;
import io.fabric8.tekton.pipeline.v1.PipelineRunBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
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

    public final String PIPELINE_RUN_GENERATE_NAME = "task-docker-";

    public final String REF_PIPELINE_NAME = "task-docker";

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
            PipelineRunBuilder pipelineRunBuilder = TektonResourceBuilder.createPipelineRunBuilder(
                    runtimeInfo, namespace, PIPELINE_RUN_GENERATE_NAME, REF_PIPELINE_NAME);
            pipelineRunBuilder.editSpec()
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
                    .editFirstTaskRunSpec()
                    .withServiceAccountName(serviceAccountName)
                    .endTaskRunSpec()
                    .editLastTaskRunSpec()
                    .withServiceAccountName(serviceAccountForPostTask)
                    .endTaskRunSpec()
                    .endSpec();
            return tektonClient.v1().pipelineRuns().resource(pipelineRunBuilder.build()).create();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private String prepareResource(DockerBuildData data) {
        // create a 5 chars random string for secret name which starts with "auto-git-auth-"
        String secretName = ("docker-auth-" + data.getUsername()).toLowerCase();
        String serviceAccountName = ("sa-with-secret-" + data.getUsername()).toLowerCase();

        // create secret
        Secret secret = K8sResourceBuilder.createDockerBasicAuthSecret(secretName, namespace,
                data.getUsername(), new String(Base64.getDecoder().decode(data.getPassword())), data.getRepository());
        kubernetesClient.secrets().resource(secret).serverSideApply();

        // create service account linked with secret created above
        ServiceAccount serviceAccount = K8sResourceBuilder.createServiceAccountLinkedWithSecret(
                serviceAccountName, namespace, secretName);
        kubernetesClient.serviceAccounts().resource(serviceAccount).serverSideApply();

        return serviceAccountName;
    }

}
