package com.solo.tekton.mq.consumer.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployScriptAndHostsDTO implements Serializable {

    private List<Host> hostList;
    private String deployScript;

    @Data
    public static class Host {
        private Long hostId;

        private String hostDesc;

        private String hostDomain;

        private String hostIp;

        private Integer hostPort;

        private String hostAccount;

        private String hostPassword;
    }
}
