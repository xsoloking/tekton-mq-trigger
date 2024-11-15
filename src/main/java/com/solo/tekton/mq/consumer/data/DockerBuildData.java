package com.solo.tekton.mq.consumer.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerBuildData  implements Serializable {

    String context;
    String dockerfile;
    String repository;
    String username;
    String password;
    String[] tags;
    String platform;

}
