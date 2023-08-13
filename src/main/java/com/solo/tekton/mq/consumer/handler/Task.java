package com.solo.tekton.mq.consumer.handler;

public interface Task {
    void execute();
    void setup();
}
