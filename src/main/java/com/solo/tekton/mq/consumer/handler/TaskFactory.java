package com.solo.tekton.mq.consumer.handler;

public class TaskFactory {
    public static Task createTask(RuntimeInfo runtimeInfo) {
        return TaskType.valueOf(runtimeInfo.getProject()).getConstructor().apply(runtimeInfo);
    }
}
