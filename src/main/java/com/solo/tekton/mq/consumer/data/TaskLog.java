package com.solo.tekton.mq.consumer.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskLog implements Serializable {

    @Id
    public String _id;
    public Long flowInstanceId;
    public Long nodeInstanceId;
    public Long taskInstanceId;
    public Long executeBatchId;
    public String logContent;
    public boolean htmlLog;
    public int logType;
    public long timeout;
    public String pipelineRunName;
}
