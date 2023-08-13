package com.solo.tekton.mq.consumer.handler;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TaskMaven implements Task {

    @NonNull
    private RuntimeInfo runtimeInfo;

    @Override
    public void execute() {
        System.out.println("Git");
    }

    @Override
    public void setup() {
        System.out.println("Git setup");
    }
}
