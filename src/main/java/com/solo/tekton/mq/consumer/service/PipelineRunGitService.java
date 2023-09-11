package com.solo.tekton.mq.consumer.service;

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

import java.text.ParseException;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class PipelineRunGitService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Git";

    public final String PIPELINE_RUN_GENERATE_NAME = "task-git-";

    public final String REF_PIPELINE_NAME = "task-git";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PipelineRun createPipelineRun(RuntimeInfo runtimeInfo) {
        Map<String, String> params = runtimeInfo.getParams();
        String gitServiceAccountName = prepareResource(params);
        try {
            PipelineRunBuilder pipelineRunBuilder = TektonResourceBuilder.createPipelineRunBuilder(
                    runtimeInfo, namespace, PIPELINE_RUN_GENERATE_NAME, REF_PIPELINE_NAME);
            pipelineRunBuilder.editSpec()
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
                    .editFirstTaskRunSpec()
                        .withServiceAccountName(gitServiceAccountName)
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


    private String prepareResource(Map<String, String> params) {
        // create a 5 chars random string for secret name which starts with "auto-git-auth-"
        String encodedToken = params.get("CREDENTIALS_ID");
        String token = new String(Base64.getDecoder().decode(encodedToken));
        String postfix = encodedToken.substring(encodedToken.length() / 3, encodedToken.length() / 2);
        String secretName = ("git-auth-" + postfix).toLowerCase();
        String serviceAccountName = ("sa-with-secret-" + postfix).toLowerCase();

        // create secret
        Secret secret = K8sResourceBuilder.createGitBasicAuthSecret(secretName, namespace,
                "git", token, Common.extractServerUrl(params.get("GIT_URL")));
        kubernetesClient.secrets().resource(secret).serverSideApply();

        // create service account linked with secret created above
        ServiceAccount serviceAccount = K8sResourceBuilder.createServiceAccountLinkedWithSecret(
                serviceAccountName, namespace, secretName);
        kubernetesClient.serviceAccounts().resource(serviceAccount).serverSideApply();

        return serviceAccountName;
    }

}
