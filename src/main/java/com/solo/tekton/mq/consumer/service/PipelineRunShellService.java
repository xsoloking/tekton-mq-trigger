package com.solo.tekton.mq.consumer.service;

import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.utils.TektonResourceBuilder;
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
import java.util.Map;

@Service
@Slf4j
public class PipelineRunShellService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Bash";

    public final String PIPELINE_RUN_GENERATE_NAME = "task-shell-";

    public final String REF_PIPELINE_NAME = "task-shell";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PipelineRun createPipelineRun(RuntimeInfo runtimeInfo) {
        Map<String, String> params = runtimeInfo.getParams();
        try {
            PipelineRunBuilder pipelineRunBuilder = pipelineRunBuilder = TektonResourceBuilder.createPipelineRunBuilder(
                    runtimeInfo, namespace, PIPELINE_RUN_GENERATE_NAME, REF_PIPELINE_NAME);
            pipelineRunBuilder.editSpec()
//                    .addToParams(new ParamBuilder()
//                            .withName("WORKING_PATH")
//                            .withNewValue(params.get("SOURCE"))
//                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_IMAGE")
                            .withNewValue(params.get("TASK_IMAGE"))
                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_SCRIPT")
                            .withNewValue(params.get("SCRIPT"))
                            .build())
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
