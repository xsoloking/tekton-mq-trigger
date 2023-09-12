package com.solo.tekton.mq.consumer.runner;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.Pipeline;
import io.fabric8.tekton.pipeline.v1.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateUpdateTektonResourcesRunner implements CommandLineRunner {

    @Autowired
    KubernetesClient kubernetesClient;

    @Autowired
    TektonClient tektonClient;

    @Value("${flow.k8s.namespace}")
    private String namespace;

    /**
     * Callback used to run the bean.
     *
     * @param args incoming main method arguments
     * @throws Exception on error
     */
    @Override
    public void run(String... args) throws Exception {
        // Load files from resources/tekton-res/dependencies/secret and apply with kubernetesClient
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/tekton-res/dependencies/secrets/*.yaml");
        for (Resource resource: resources) {
            Secret res = kubernetesClient.secrets().load(resource.getInputStream()).item();
            try {
                kubernetesClient.secrets().inNamespace(namespace).resource(res).serverSideApply();
            } catch (KubernetesClientException e) {
                log.warn("Create/update resource {} was failed due to exception: {}", resource.getFilename(), e.getMessage());
            }
        }

        // Load files from resources/tekton-res/dependencies/serviceAccounts and apply with kubernetesClient
        resources = resolver.getResources("classpath:/tekton-res/dependencies/serviceAccounts/*.yaml");
        for (Resource resource: resources) {
            ServiceAccount res = kubernetesClient.serviceAccounts().load(resource.getInputStream()).item();
            try {
                kubernetesClient.serviceAccounts().inNamespace(namespace).resource(res).serverSideApply();
            } catch (KubernetesClientException e) {
                log.warn("Create/update resource {} was failed due to exception: {}", resource.getFilename(), e.getMessage());
            }
        }

        // Load files from resources/tekton-res/task and apply with tektonClient
        resources = resolver.getResources("classpath:/tekton-res/task/*.yaml");
        for (Resource resource: resources) {
            Task res = tektonClient.v1().tasks().load(resource.getInputStream()).item();
            try {
                tektonClient.v1().tasks().inNamespace(namespace).resource(res).serverSideApply();
            } catch (KubernetesClientException e) {
                log.warn("Create/update resource {} was failed due to exception: {}", resource.getFilename(), e.getMessage());
            }
        }

        // Load files from resources/tekton-res/pipeline and apply with tektonClient
        resources = resolver.getResources("classpath:/tekton-res/pipeline/*.yaml");
        for (Resource resource: resources) {
            Pipeline res = tektonClient.v1().pipelines().load(resource.getInputStream()).item();
            try {
                tektonClient.v1().pipelines().inNamespace(namespace).resource(res).serverSideApply();
            } catch (KubernetesClientException e) {
                log.warn("Create/update resource {} was failed due to exception: {}", resource.getFilename(), e.getMessage());
            }
        }
    }
}
