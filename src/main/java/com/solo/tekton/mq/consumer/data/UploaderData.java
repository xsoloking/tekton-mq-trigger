package com.solo.tekton.mq.consumer.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploaderData implements Serializable {

    public String destFilePath;
    public String password;
    public String user;
    public String workspace;
    public String compressName;
    public String uploadDir;
}
