package com.solo.tekton.mq.consumer.handler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;


@Getter
@RequiredArgsConstructor
public enum TaskType {

    TaskGit("JJB_Task_Git", com.solo.tekton.mq.consumer.handler.TaskGit::new),
    TaskMaven("JJB_Task_Maven", com.solo.tekton.mq.consumer.handler.TaskMaven::new);

    private final String name;
    private final Function<RuntimeInfo, Task> constructor;
}
