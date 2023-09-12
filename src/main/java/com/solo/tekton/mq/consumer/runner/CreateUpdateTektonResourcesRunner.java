package com.solo.tekton.mq.consumer.runner;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1.Pipeline;
import io.fabric8.tekton.pipeline.v1.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
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

    @Autowired
    ApplicationContext applicationContext;

    /**
     * Callback used to run the bean.
     *
     * @param args incoming main method arguments
     * @throws Exception on error
     */
    @Override
    public void run(String... args) throws Exception {
        // Load files from resources/tekton-res/dependencies and apply with kubernetesClient
        Resource[] resources = applicationContext.getResources("classpath:/tekton-res/dependencies/secrets/*.*");
        for (Resource resource: resources) {
            Secret res = kubernetesClient.secrets().load(resource.getFile()).item();
            kubernetesClient.secrets().inNamespace(namespace).resource(res).serverSideApply();
        }

        // Load files from resources/tekton-res/dependencies and apply with kubernetesClient
        resources = applicationContext.getResources("classpath:/tekton-res/dependencies/serviceAccounts/*.*");
        for (Resource resource: resources) {
            ServiceAccount res = kubernetesClient.serviceAccounts().load(resource.getFile()).item();
            kubernetesClient.serviceAccounts().inNamespace(namespace).resource(res).serverSideApply();
        }

        // Load files from resources/tekton-res/dependencies and apply with kubernetesClient
        resources = applicationContext.getResources("classpath:/tekton-res/task/*.*");
        for (Resource resource: resources) {
            Task res = tektonClient.v1().tasks().load(resource.getFile()).item();
            tektonClient.v1().tasks().inNamespace(namespace).resource(res).serverSideApply();
        }

        // Load files from resources/tekton-res/dependencies and apply with kubernetesClient
        resources = applicationContext.getResources("classpath:/tekton-res/pipeline/*.*");
        for (Resource resource: resources) {
            Pipeline res = tektonClient.v1().pipelines().load(resource.getFile()).item();
            tektonClient.v1().pipelines().inNamespace(namespace).resource(res).serverSideApply();
        }
    }
}
