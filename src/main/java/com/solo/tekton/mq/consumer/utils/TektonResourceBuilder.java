package com.solo.tekton.mq.consumer.utils;

import com.solo.tekton.mq.consumer.data.RuntimeInfo;
import io.fabric8.kubernetes.api.model.Duration;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.tekton.pipeline.v1.*;

import java.text.ParseException;
import java.util.Map;

public class TektonResourceBuilder {

    public static PipelineRunBuilder createPipelineRunBuilder(
            RuntimeInfo runtimeInfo, String namespace, String generateName, String refPipelineName
    ) throws ParseException {
        Map<String, String> params = runtimeInfo.getParams();
        String nodeSelector = "kubernetes.io/os: \"linux\"";
        if (params.containsKey("TASK_NODE_SELECTOR") && params.get("TASK_NODE_SELECTOR") != null) {
            nodeSelector = params.get("TASK_NODE_SELECTOR");
        }
        int taskTimeout = 30;
        if (params.containsKey("TASK_TIMEOUT") && params.get("TASK_TIMEOUT") != null) {
            taskTimeout = Integer.parseInt(params.get("TASK_TIMEOUT"));
        }
        int pipelineTimeout = taskTimeout + 5;
        PipelineRunBuilder pipelineRunBuilder = new PipelineRunBuilder()
                .withNewMetadata()
                .withGenerateName(generateName)
                .withNamespace(namespace)
                .addToLabels("devops.flow/tenantId", params.get("tenantCode"))
                .addToLabels("devops.flow/systemId", params.get("systemId"))
                .addToLabels("devops.flow/flowId", params.get("flowId"))
                .addToLabels("devops.flow/flowInstanceId", params.get("flowInstanceId"))
                .addToLabels("devops.flow/taskInstanceId", params.get("taskInstanceId"))
                .endMetadata()
                .withNewSpec()
                .withNewPipelineRef()
                .withName(refPipelineName)
                .endPipelineRef()
                .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                        .withPipelineTaskName("main")
                        .withNewPodTemplate()
                        .addToNodeSelector(nodeSelector.split(": ")[0], nodeSelector.split(": ")[1].replaceAll("\"", ""))
                        .endPodTemplate()
                        .build())
                .addToTaskRunSpecs(new PipelineTaskRunSpecBuilder()
                        .withPipelineTaskName("post")
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
                .withNewTimeouts()
                .withPipeline(Duration.parse(pipelineTimeout + "m"))
                .withTasks(Duration.parse(taskTimeout + "m"))
                .withFinally(Duration.parse("3m"))
                .endTimeouts()
                .endSpec();
        return pipelineRunBuilder;
    }

    public static PipelineRunBuilder createPipelineRunBuilderForShell(
            RuntimeInfo runtimeInfo, String namespace, String generateName, String refPipelineName
    ) throws ParseException {
        Map<String, String> params = runtimeInfo.getParams();
        PipelineRunBuilder pipelineRunBuilder = createPipelineRunBuilder(
                runtimeInfo, namespace, generateName, refPipelineName);
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
                        .withNewValue(params.get("SCRIPT"))
                        .build())
                .endSpec();
        return pipelineRunBuilder;
    }

    public static PipelineRunBuilder createPipelineRunBuilderForShellWithCache(
            RuntimeInfo runtimeInfo, String namespace, String generateName, String refPipelineName, String cachePath
    ) throws ParseException {
        Map<String, String> params = runtimeInfo.getParams();
        PipelineRunBuilder pipelineRunBuilder = createPipelineRunBuilderForShell(
                runtimeInfo, namespace, generateName, refPipelineName);
        pipelineRunBuilder.editSpec()
                .editFirstTaskRunSpec()
                .editPodTemplate()
                .addToVolumes(new VolumeBuilder()
                        .withName("build-cache")
                        .withNewHostPath(cachePath, "DirectoryOrCreate")
                        .build())
                .endPodTemplate()
                .endTaskRunSpec()
                .endSpec();
        return pipelineRunBuilder;
    }
}
