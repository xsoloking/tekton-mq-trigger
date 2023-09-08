package com.solo.tekton.mq.consumer.data;

import lombok.Data;

import java.io.Serializable;

@Data
public class DockerBuildData  implements Serializable {

    String context;
    String dockerfile;
    String repository;
    String username;
    String password;
    String[] tags;
    String platform;

}
