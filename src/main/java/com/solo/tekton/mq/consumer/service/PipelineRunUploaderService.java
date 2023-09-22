package com.solo.tekton.mq.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import com.solo.tekton.mq.consumer.data.UploaderData;
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

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class PipelineRunUploaderService implements PipelineRunService {

    @Autowired
    private TektonClient tektonClient;

    @Autowired
    private KubernetesClient kubernetesClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    @Value("${pipeline.run.post.task.service.account.name}")
    private String serviceAccountForPostTask;

    public final String TYPE = "JJB_Task_Uploader";

    public final String PIPELINE_RUN_GENERATE_NAME = "task-uploader-";

    public final String REF_PIPELINE_NAME = "task-uploader";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PipelineRun createPipelineRun(RuntimeInfo runtimeInfo) {
        Map<String, String> params = runtimeInfo.getParams();
        UploaderData[] data = null;
        try {
            String json = new String(Base64.getDecoder().decode(params.get("DATA2DEPLOY")));
            data = new ObjectMapper().readValue(json, UploaderData[].class);
        } catch (IOException e) {
            log.error("The value of the parameter \"DATA2DEPLOY\" is invalid, please check the exception: {}", e.getMessage());
        }

        if (data == null || data.length == 0) {
            log.error("The parameter \"DATA2DEPLOY\" is invalid, the task is failed: {}", runtimeInfo);
            throw new RuntimeException("The parameter \"DATA2DEPLOY\" is empty");
        }

        try {
            PipelineRunBuilder pipelineRunBuilder = TektonResourceBuilder.createPipelineRunBuilder(
                    runtimeInfo, namespace, PIPELINE_RUN_GENERATE_NAME, REF_PIPELINE_NAME);
            pipelineRunBuilder.editSpec()
//                    .addToParams(new ParamBuilder()
//                            .withName("WORKING_PATH")
//                            .withNewValue(data[0].getWorkspace())
//                            .build())
//                    .addToParams(new ParamBuilder()
//                            .withName("TASK_IMAGE")
//                            .withNewValue(params.get("TASK_IMAGE"))
//                            .build())
                    .addToParams(new ParamBuilder()
                            .withName("TASK_SCRIPT")
                            .withNewValue(prepareShellScript(data[0]))
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

    private String prepareShellScript(UploaderData data) {
        String script = "#!/usr/bin/env bash";
        script = script.concat(System.getProperty("line.separator"));
        script = script.concat("set -x");
        script = script.concat(System.getProperty("line.separator"));
        if (data.getCompressName().endsWith("tar")) {
            script = script.concat("tar -cvf " + data.getCompressName() + " " + data.getDestFilePath());
        } else {
            // zip
            script = script.concat("zip -r " + data.getCompressName() + " " + data.getDestFilePath());
        }
        script = script.concat(System.getProperty("line.separator"));
        // curl -v -u ${auth} --upload-file ${fileName} ${requestUrl}
        script = script.concat("sex +x; curl -v -u " + data.getUser() + ":" + data.getPassword() + " --upload-file " + data.getCompressName() + " " + data.getUploadDir());
        script = script.concat(System.getProperty("line.separator"));
        // date '+%Y-%m-%d %H:%M:%S'
        script = script.concat("echo data=$(date '+%Y-%m-%d %H:%M:%S') > metadata.info");
        script = script.concat(System.getProperty("line.separator"));
        script = script.concat("md5sum " + data.getCompressName() + " | awk '{print \"md5=\"$1}' >> metadata.info");
        script = script.concat(System.getProperty("line.separator"));
        script = script.concat("ls -l " + data.getCompressName() + " | awk '{print \"size=\"$5}' >> metadata.info");
        script = script.concat(System.getProperty("line.separator"));
        script = script.concat("echo compressName=" + data.getCompressName() + " >> metadata.info");
        return Base64.getEncoder().encodeToString(script.getBytes());
    }

}
