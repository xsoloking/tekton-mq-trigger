package com.solo.tekton.mq.consumer.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.TektonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesConfiguration {

    @Value("${flow.k8s.token}")
    private String k8sToken;
    @Value("${flow.k8s.url}")
    private String k8sUrl;

    @Bean
    public KubernetesClient kubernetesClient() {
        Config config = new ConfigBuilder()
                .withMasterUrl(k8sUrl)
                .withOauthToken(k8sToken)
                .withTrustCerts()
                .build();
        if (k8sUrl.equals("built-in-k3s")) {
            config = new ConfigBuilder().withAutoConfigure().withTrustCerts().build();
        }
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    @Bean
    public TektonClient tektonClient() {
        Config config = new ConfigBuilder()
                .withMasterUrl(k8sUrl)
                .withOauthToken(k8sToken)
                .withTrustCerts()
                .build();
        if (k8sUrl.equals("built-in-k3s")) {
            config = new ConfigBuilder().withAutoConfigure().withTrustCerts().build();
        }
        return new KubernetesClientBuilder().withConfig(config).build().adapt(TektonClient.class);
    }
}
