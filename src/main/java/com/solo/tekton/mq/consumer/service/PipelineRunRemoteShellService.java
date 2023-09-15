package com.solo.tekton.mq.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.DeployScriptAndHostsDTO;
import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.utils.K8sResourceBuilder;
import com.solo.tekton.mq.consumer.utils.TektonResourceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSource;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
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
public class PipelineRunRemoteShellService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Ssh_Docker_Deploy";

    public final String PIPELINE_RUN_GENERATE_NAME = "task-remote-shell-";

    public final String REF_PIPELINE_NAME = "task-remote-shell";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PipelineRun createPipelineRun(RuntimeInfo runtimeInfo) {
        Map<String, String> params = runtimeInfo.getParams();
        ObjectMapper mapper = new ObjectMapper();
        DeployScriptAndHostsDTO data = null;
        try {
            data = mapper.readValue(Base64.getDecoder().decode(params.get("SCRIPT")), DeployScriptAndHostsDTO.class);
        } catch (IOException e) {
            log.error("Task was failed due to failed to parse message: {}", params.get("SCRIPT"));
            throw new RuntimeException("Task was failed due to failed to parse message: " + params.get("SCRIPT"));
        }

        StringBuilder secretName = new StringBuilder("host-");
        String dataValue = "[all]";
        for (DeployScriptAndHostsDTO.Host host: data.getHostList()) {
            secretName.append(String.join("-", host.getHostIp().split("\\.")));
            dataValue = dataValue.concat(System.getProperty("line.separator"));
            dataValue = dataValue.concat(host.getHostIp()
                    + " ansible_ssh_port=" + host.getHostPort()
                    + " ansible_user=" + host.getHostAccount()
                    + " ansible_ssh_pass='" + new String(Base64.getDecoder().decode(host.getHostPassword())) +"'");
        }

        Secret secret = K8sResourceBuilder.createSecret(secretName.toString(), namespace, "hosts", dataValue);
        kubernetesClient.secrets().inNamespace(namespace).resource(secret).serverSideApply();
        SecretVolumeSource secretVolumeSource = new SecretVolumeSource();
        secretVolumeSource.setSecretName(secretName.toString());
        try {
            PipelineRunBuilder pipelineRunBuilder = TektonResourceBuilder.createPipelineRunBuilder(
                    runtimeInfo, namespace, PIPELINE_RUN_GENERATE_NAME, REF_PIPELINE_NAME);
            pipelineRunBuilder.editSpec()
                    .addToParams(new ParamBuilder()
                            .withName("WORKING_PATH")
                            .withNewValue(params.get("SOURCE"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_IMAGE")
                            .withNewValue(params.get("TASK_IMAGE"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_SCRIPT")
                            .withNewValue(new String(Base64.getEncoder().encode(data.getDeployScript().getBytes())))
                            .build())
                    .editFirstTaskRunSpec()
                    .editPodTemplate()
                    .addToVolumes(new VolumeBuilder()
                            .withName("secret-volume")
                            .withNewSecretLike(secretVolumeSource)
                            .endSecret()
                            .build())
                    .endPodTemplate()
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
}
