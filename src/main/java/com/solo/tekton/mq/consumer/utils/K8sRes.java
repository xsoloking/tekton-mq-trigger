package com.solo.tekton.mq.consumer.utils;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.HashMap;

public class K8sRes {

    /**
     * Create secret for git basic auth will be used in Tekton pipeline
     * secret name
     *
     * Params: k8sClient - a kubernetes client used to create resource
     *         secretName - secret name
     *         gitUrl - git repo url used to get git server url
     *         token - gitlab/github server's personal token used to access git server
     * Returns: Object
     **/
    public static Object createGitSecret(KubernetesClient k8sClient, String ns, String name, String gitUrl, String token) {
        /*
        Here is the example yaml file for git basic auth secret：
        apiVersion: v1
        kind: Secret
        metadata:
          name: github-basic-auth
          annotations:
            tekton.dev/git-0: https://github.com # Described below
        type: kubernetes.io/basic-auth
        stringData:
          username: git
          password: token
         */

        Secret secret = new SecretBuilder()
                .withApiVersion("v1")
                .withKind("Secret")
                .withNewMetadata()
                .withNamespace(ns)
                .withName(name)
                .addToAnnotations("tekton.dev/git-0", Common.extractGitServerUrl(gitUrl))
                .endMetadata()
                .withType("kubernetes.io/basic-auth")
                .withStringData(new HashMap<String, String>() {{
                    put("username", "git");
                    put("password", token);
                }})
                .build();

        return k8sClient.secrets().resource(secret).serverSideApply();
    }


    /**
     * Create service account and bind a secret
     * @param k8sClient
     * @param ns
     * @param name
     * @param secretName
     * @return
     */
    public static Object createServiceAccountWithSecret(KubernetesClient k8sClient, String ns, String name, String secretName) {
        ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withApiVersion("v1")
                .withKind("ServiceAccount")
                .withNewMetadata()
                .withNamespace(ns)
                .withName(name)
                .endMetadata()
                .withSecrets()
                .addToSecrets(new ObjectReferenceBuilder()
                        .withKind("Secret")
                        .withNamespace(ns)
                        .withName(secretName)
                        .build())
                .build();
        return k8sClient.serviceAccounts().resource(serviceAccount).serverSideApply();
    }
}
