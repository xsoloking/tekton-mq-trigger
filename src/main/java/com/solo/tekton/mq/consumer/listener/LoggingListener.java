package com.solo.tekton.mq.consumer.listener;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

@Component
@Slf4j
public class LoggingListener {

    @Autowired
    KubernetesClient kubernetesClient;
    @RabbitListener(queues = "tasks-logging")
    public void receiveMessage(String taskInstanceId) throws IOException, InterruptedException {
        log.info("Received message: " + taskInstanceId);
        final CountDownLatch watchLatch = new CountDownLatch(1);
        Watcher<Pod> watcher = new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod aPod) {
                log.info(aPod.getMetadata().getName(), aPod.getStatus().getPhase());
                if (aPod.getStatus().getPhase().equals("Succeeded")) {
                    log.info("Logs -> ", kubernetesClient.pods().inNamespace("default").withName(aPod.getMetadata().getName()).inContainer("step-executor").getLog());
                    watchLatch.countDown();
                }
            }

            @Override
            public void onClose(WatcherException e) {
                // Ignore
            }
        };
        kubernetesClient.pods().inNamespace("default")
                .withLabel("devops.flow/taskInstanceId", taskInstanceId)
                .withLabel("tekton.dev/pipelineTask", "main").watch(watcher);
    }
}
