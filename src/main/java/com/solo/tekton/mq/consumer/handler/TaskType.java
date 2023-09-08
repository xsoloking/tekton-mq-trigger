package com.solo.tekton.mq.consumer.handler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;


@Getter
@RequiredArgsConstructor
public enum TaskType {

    JJB_Task_Git("JJB_Task_Git", com.solo.tekton.mq.consumer.handler.TaskGit::new),
    JJB_Task_Maven("JJB_Task_Maven", com.solo.tekton.mq.consumer.handler.TaskMaven::new),
    JJB_Task_Docker("JJB_Task_Docker", com.solo.tekton.mq.consumer.handler.TaskDocker::new);

    private final String name;
    private final Function<RuntimeInfo, BaseTask> constructor;
}
