package com.solo.tekton.mq.consumer;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class Runner implements CommandLineRunner {


	@Autowired
	KubernetesClient kubernetesClient;

	@Override
	public void run(String... args) throws Exception {
//		Object o = K8sRes.createGitSecret(kubernetesClient, "default", "git-basic-auth-01", "https://github.com/org/repo.git", "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");
//		log.info("secret: {}", o);
//		Object o = K8sRes.createServiceAccountWithSecret(kubernetesClient, "default", "sa-01", "git-basic-auth-01");
//		log.info("service account: {}", o);
    }

}
