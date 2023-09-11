package com.solo.tekton.mq.consumer.utils;

import io.fabric8.kubernetes.api.model.*;

import java.util.HashMap;
import java.util.Map;

public class K8sResourceBuilder {

    /**
     * Creates a basic authentication secret with the given parameters.
     *
     * @param  secretName   the name of the secret
     * @param  namespace    the namespace in which the secret will be created
     * @param  username     the username for the basic authentication
     * @param  password     the password for the basic authentication
     * @param  annotations  the annotations to be added to the secret
     * @return              the created Secret object
     */
    public static Secret createBasicAuthSecret(String secretName, String namespace, String username,
                                                      String password, Map<String, String> annotations) {
        return new SecretBuilder()
                .withApiVersion("v1")
                .withKind("Secret")
                .withNewMetadata()
                .withName(secretName)
                .withNamespace(namespace)
                .addToAnnotations(annotations)
                .endMetadata()
                .withType("kubernetes.io/basic-auth")
                .withStringData(new HashMap<>() {{
                    put("username", username);
                    put("password", password);
                }}).build();
    }

    /**
     * Creates a Git basic authentication secret builder.
     *
     * @param  secretName    the name of the secret
     * @param  namespace     the namespace of the secret
     * @param  username      the username for authentication
     * @param  password      the password for authentication
     * @param  gitServerUrl  the URL of the Git server, e.g. https://github.com
     * @return               the created Git basic authentication secret builder
     */
    public static Secret createGitBasicAuthSecret(String secretName, String namespace, String username,
                                                         String password, String gitServerUrl) {
        return createBasicAuthSecret(secretName, namespace, username, password, new HashMap<>() {{
            put("tekton.dev/git-0", gitServerUrl);
        }});
    }


    /**
     * Creates a Docker basic authentication secret builder.
     *
     * @param  secretName      the name of the secret
     * @param  namespace       the namespace of the secret
     * @param  username        the username for authentication
     * @param  password        the password for authentication
     * @param  registryHostPort    the host and port of the registry, e.g. localhost:5000
     * @return                 the created Docker basic authentication secret builder
     */
    public static Secret createDockerBasicAuthSecret(String secretName, String namespace, String username,
                                                            String password, String registryHostPort) {
        return createBasicAuthSecret(secretName, namespace, username, password, new HashMap<>() {{
            put("tekton.dev/docker-0", registryHostPort);
        }});
    }

    /**
     * Creates a new ServiceAccount with the given service account name, namespace, and secret name.
     *
     * @param  serviceAccountName  the name of the service account
     * @param  namespace           the namespace in which the service account will be created
     * @param  secretName          the name of the secret to be added to the service account
     * @return                     the created ServiceAccount object
     */
    public static ServiceAccount createServiceAccountLinkedWithSecret(String serviceAccountName, String namespace, String secretName) {
        return new ServiceAccountBuilder()
                .withApiVersion("v1")
                .withKind("ServiceAccount")
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(serviceAccountName)
                .endMetadata()
                .withSecrets()
                .addToSecrets(new ObjectReferenceBuilder()
                        .withKind("Secret")
                        .withName(secretName)
                        .withApiVersion("v1")
                        .build())
                .build();
    }
}
