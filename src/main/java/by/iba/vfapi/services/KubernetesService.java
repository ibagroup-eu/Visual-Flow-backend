/*
 * Copyright (c) 2021 IBA Group, a.s. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package by.iba.vfapi.services;

import by.iba.vfapi.dao.PodEventRepositoryImpl;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.LogDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.model.PodEvent;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.RequestConfigBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.FunctionCallable;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * KubernetesService class.
 */
@Service
@Slf4j
@Getter
public class KubernetesService {
    public static final String NO_POD_MESSAGE = "Pod doesn't exist";
    private static final String POD_STOP_COMMAND = "pkill -SIGTERM -u job-user";
    private static final int SERVICE_ACCOUNT_WAIT_PERIOD_MIN = 2;
    protected final String appName;
    protected final String appNameLabel;
    protected final String pvcMountPath;
    protected final NamespacedKubernetesClient client;
    protected final NamespacedKubernetesClient userClient;
    protected final AuthenticationService authenticationService;
    private final PodEventRepositoryImpl podEventRepository;

    public KubernetesService(
        final NamespacedKubernetesClient client,
        @Value("${namespace.app}") final String appName,
        @Value("${namespace.label}") final String appNameLabel,
        @Value("${pvc.mountPath}") final String pvcMountPath,
        final AuthenticationService authenticationService,
        final PodEventRepositoryImpl podEventRepository) {
        this.appName = appName;
        this.appNameLabel = appNameLabel;
        this.pvcMountPath = pvcMountPath;
        this.authenticationService = authenticationService;
        this.podEventRepository = podEventRepository;
        this.client = client;
        this.userClient = new DefaultKubernetesClient(new ConfigBuilder(client.getConfiguration())
                                                          .withClientKeyFile(null)
                                                          .withClientCertData(null)
                                                          .withClientCertFile(null)
                                                          .withClientKeyData(null)
                                                          .build());
    }

    /**
     * Helper method to generate unique name for k8s entity
     *
     * @param nameValidator supposed to check whether entity with such name already exists in k8s
     * @return unique name
     */
    public static String getUniqueEntityName(
        Function<String, Object> nameValidator) {
        String name = UUID.randomUUID().toString();
        while (true) {
            try {
                Object apply = nameValidator.apply(name);
                if (apply == null) {
                    return name;
                }
                name = UUID.randomUUID().toString();
            } catch (ResourceNotFoundException e) {
                break;
            }
        }
        return name;
    }

    /**
     * Parses provided logs
     *
     * @param logSupplier log supplier
     * @return list of parsed log objects
     */
    public static List<LogDto> getParsedLogs(Supplier<String> logSupplier) {
        try {
            String logs = logSupplier.get();
            String[] logItems = logs.split("\n");
            List<LogDto> logResults = new ArrayList<>();

            int logIndex = 0;
            for (String logItem : logItems) {
                Matcher matcher = K8sUtils.LOG_PATTERN.matcher(logItem);
                if (matcher.matches()) {
                    logResults.add(LogDto.fromMatcher(matcher));
                    logIndex++;
                } else if (logIndex != 0) {
                    LogDto lastLog = logResults.get(logIndex - 1);
                    logResults.set(logIndex - 1, lastLog.withMessage(lastLog.getMessage() + "\n" + logItem));
                }
            }

            if (logResults.isEmpty()) {
                logResults.add(LogDto.builder().message(logs).build());
            }

            return logResults;
        } catch (ResourceNotFoundException e) {
            LOGGER.info(NO_POD_MESSAGE, e);
            return Collections.emptyList();
        }
    }

    protected FunctionCallable<NamespacedKubernetesClient> getAuthenticatedClient(NamespacedKubernetesClient kbClient) {
        Secret secret = getServiceAccountSecret(authenticationService.getUserInfo().getUsername());
        String k8sToken = new String(Base64.decodeBase64(secret.getData().get(Constants.TOKEN)),
                StandardCharsets.UTF_8);
        return kbClient.withRequestConfig(new RequestConfigBuilder().withOauthToken(k8sToken).build());
    }

    protected <T> T authenticatedCall(Function<NamespacedKubernetesClient, T> caller) {
        if (authenticationService.getUserInfo().isSuperuser()) {
            return caller.apply(client);
        } else {
            return getAuthenticatedClient(this.userClient).call(caller);
        }
    }

    /**
     * Creates new namespace.
     *
     * @param namespace namespace.
     */
    public void createNamespace(final Namespace namespace) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .namespaces()
            .create(new NamespaceBuilder(namespace)
                        .editMetadata()
                        .addToLabels(K8sUtils.APP, appNameLabel)
                        .endMetadata()
                        .build()));
    }

    /**
     * Gets namespace by name.
     *
     * @param namespace name of namespace.
     * @return Namespace object.
     */
    public Namespace getNamespace(String namespace) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .namespaces()
            .withName(namespace)
            .require());
    }

    /**
     * Gets namespace list.
     *
     * @return namespace list.
     */
    public List<Namespace> getNamespaces() {
        return client.namespaces().withLabel(K8sUtils.APP, appNameLabel).list().getItems();
    }

    /**
     * Changes namespace description.
     *
     * @param namespace   name of namespace.
     * @param description new project description.
     */
    public void editDescription(final String namespace, final String description) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .namespaces()
            .withName(namespace)
            .edit((Namespace ns) -> {
                ns.setMetadata(new ObjectMetaBuilder(ns.getMetadata())
                                   .addToAnnotations(Constants.DESCRIPTION_FIELD, description)
                                   .build());
                return ns;
            }));
    }

    /**
     * Deletes namespace
     *
     * @param namespace name of namespace.
     */
    public void deleteNamespace(String namespace) {
        authenticatedCall(authenticatedClient -> authenticatedClient.namespaces().withName(namespace).delete());
    }

    /**
     * Creates or updates resource quota.
     *
     * @param namespace     namespace.
     * @param resourceQuota resource quota.
     */
    public void createOrReplaceResourceQuota(final String namespace, final ResourceQuota resourceQuota) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .resourceQuotas()
            .inNamespace(namespace)
            .createOrReplace(new ResourceQuotaBuilder(resourceQuota)
                                 .editMetadata()
                                 .addToLabels(K8sUtils.APP, appNameLabel)
                                 .endMetadata()
                                 .build()));
    }

    /**
     * Gets resource quota info.
     *
     * @param namespace name of namespace.
     * @param quotaName name of quota.
     * @return resource quota info.
     */
    public ResourceQuota getResourceQuota(final String namespace, final String quotaName) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .resourceQuotas()
            .inNamespace(namespace)
            .withName(quotaName)
            .require());
    }

    /**
     * Creates or updates secret.
     *
     * @param namespace name of namespace.
     * @param secret    secret.
     */
    public void createOrReplaceSecret(final String namespace, final Secret secret) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .secrets()
            .inNamespace(namespace)
            .createOrReplace(new SecretBuilder(secret)
                                 .editMetadata()
                                 .addToLabels(K8sUtils.APP, appNameLabel)
                                 .endMetadata()
                                 .build()));
    }

    /**
     * Gets secret.
     *
     * @param namespace  name of namespace.
     * @param secretName name of the secret.
     * @return secret.
     */
    public Secret getSecret(final String namespace, final String secretName) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .secrets()
            .inNamespace(namespace)
            .withName(secretName)
            .require());
    }

    /**
     * Check if secret exists.
     *
     * @param namespace  name of namespace.
     * @param secretName name of the secret.
     * @return secret.
     */
    public Boolean isSecretExist(final String namespace, final String secretName) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .secrets()
            .inNamespace(namespace)
            .withName(secretName)
            .isReady());
    }

    /**
     * Get secrets based on certain labels.
     *
     * @param namespace k8s namespace
     * @param labels    labels
     * @return list of secrets
     */
    public List<Secret> getSecretsByLabels(final String namespace, final Map<String, String> labels) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .secrets()
            .inNamespace(namespace)
            .withLabels(labels)
            .list()
            .getItems());
    }

    /**
     * Gets resource usage info.
     *
     * @param namespace namespace.
     * @return pod metrics.
     */
    public List<PodMetrics> topPod(final String namespace) {
        return client.top().pods().metrics(namespace).getItems();
    }

    /**
     * Gets resource usage info.
     *
     * @param namespace namespace.
     * @return pod metrics.
     */
    public PodMetrics topPod(final String namespace, final String name) {
        return client.top().pods().metrics(namespace, name);
    }

    /**
     * Creates role bindings for given users and roles.
     *
     * @param namespace       k8s namespace.
     * @param roleBindingList role bindings.
     */
    public void createRoleBindings(
        final String namespace, final Collection<RoleBinding> roleBindingList) {
        List<RoleBinding> roleBindings = roleBindingList
            .stream()
            .map(roleBinding -> new RoleBindingBuilder(roleBinding)
                .editMetadata()
                .withName(K8sUtils.getValidK8sName(roleBinding.getMetadata().getName()))
                .addToLabels(K8sUtils.APP, appNameLabel)
                .endMetadata()
                .editSubject(0)
                .withName(K8sUtils.getValidK8sName(roleBinding.getSubjects().get(0).getName()))
                .withNamespace(appName)
                .endSubject()
                .build())
            .collect(Collectors.toList());

        authenticatedCall((NamespacedKubernetesClient authenticatedClient) -> {
            for (RoleBinding roleBinding : roleBindings) {
                authenticatedClient.rbac().roleBindings().inNamespace(namespace).create(roleBinding);
            }
            return null;
        });
    }

    /**
     * Creates role binding.
     *
     * @param namespace   k8s namespace.
     * @param roleBinding role binding.
     */
    public void createRoleBinding(String namespace, RoleBinding roleBinding) {
        client.rbac().roleBindings().inNamespace(namespace).create(roleBinding);
    }

    /**
     * Gets role binding.
     *
     * @param namespace k8s namespace.
     * @param name      role binding name.
     */
    public RoleBinding getRoleBinding(String namespace, String name) {
        return client.rbac().roleBindings().inNamespace(namespace).withName(name).require();
    }

    /**
     * Removes given role bindings.
     *
     * @param namespace    k8s namespace.
     * @param roleBindings role bindings.
     */
    public void deleteRoleBindings(String namespace, List<RoleBinding> roleBindings) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .rbac()
            .roleBindings()
            .inNamespace(namespace)
            .delete(roleBindings));
    }

    /**
     * Retrieves role bindings for given namespace.
     *
     * @param namespace k8s namespace.
     * @return user - role map.
     */
    public List<RoleBinding> getRoleBindings(final String namespace) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .rbac()
            .roleBindings()
            .inNamespace(namespace)
            .withLabel(K8sUtils.APP, appNameLabel)
            .list()
            .getItems());
    }

    /**
     * Creates service account for given user.
     *
     * @param userInfo user information.
     */
    public void createIfNotExistServiceAccount(final UserInfo userInfo) {
        Map<String, String> annotations = Map.of("id",
                                                 userInfo.getId(),
                                                 AccessTableDto.USERNAME,
                                                 userInfo.getUsername(),
                                                 "name",
                                                 userInfo.getName(),
                                                 "email",
                                                 userInfo.getEmail());
        String saName = K8sUtils.getValidK8sName(userInfo.getUsername());

        if (client.serviceAccounts().inNamespace(appName).withName(saName).get() == null) {
            client
                .serviceAccounts()
                .inNamespace(appName)
                .createOrReplace(new ServiceAccountBuilder()
                    .withNewMetadata()
                    .addToAnnotations(annotations)
                    .withName(saName)
                    .addToLabels(K8sUtils.APP, appNameLabel)
                    .endMetadata()
                    .build());
            LOGGER.info("{} - created a new service account - {}, waiting for it to get ready...",
                AuthenticationService.getFormattedUserInfo(userInfo),
                saName);

            client
                .serviceAccounts()
                .inNamespace(appName)
                .withName(saName)
                .waitUntilCondition(sa ->
                    sa.getSecrets().stream().anyMatch(secret -> secret.getName().contains(Constants.TOKEN)) &&
                    sa.getSecrets().stream().anyMatch(secret -> secret.getName().contains(Constants.DOCKERCFG)),
                    SERVICE_ACCOUNT_WAIT_PERIOD_MIN, TimeUnit.MINUTES);
            LOGGER.info("Service account - {} is ready now", saName);
        } else {
            LOGGER.info("{} - found existing service account - {}",
                AuthenticationService.getFormattedUserInfo(userInfo),
                saName);
        }
    }

    /**
     * Gets service account secret for given user.
     *
     * @param username user name.
     */
    public Secret getServiceAccountSecret(final String username) {
        String secretName = client
            .serviceAccounts()
            .inNamespace(appName)
            .withName(K8sUtils.getValidK8sName(username))
            .require()
            .getSecrets()
            .stream()
            .filter(secret -> secret.getName().contains(Constants.TOKEN))
            .findFirst()
            .orElseThrow(() -> new InternalProcessingException("Unable to find service account secret for user: " +
                                                                   username))
            .getName();
        return client.secrets().inNamespace(appName).withName(secretName).require();
    }

    /**
     * Retrieves service accounts.
     *
     * @return service account info.
     */
    public List<ServiceAccount> getServiceAccounts() {
        return client
            .serviceAccounts()
            .inNamespace(appName)
            .withLabel(K8sUtils.APP, appNameLabel)
            .list()
            .getItems();
    }

    /**
     * Retrieves application roles.
     *
     * @return application roles.
     */
    public List<ClusterRole> getRoles() {
        return client
            .rbac()
            .clusterRoles()
            .withLabel(K8sUtils.APPLICATION_ROLE, String.valueOf(Boolean.TRUE))
            .list()
            .getItems();
    }

    /**
     * Getting pods by labels.
     *
     * @param namespaceId namespace name
     * @param labels      map of labels
     * @return pods
     */
    public List<Pod> getPodsByLabels(final String namespaceId, final Map<String, String> labels) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespaceId)
            .withLabels(labels)
            .list()
            .getItems());
    }

    /**
     * Getting pods by labels.
     *
     * @param namespaceId namespace name
     * @param nodeId      nodeId in labels
     * @return pods
     */
    public List<Pod> getWorkflowPods(final String namespaceId, final String nodeId) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespaceId)
            .withLabel(Constants.JOB_ID_LABEL, nodeId)
            .withLabel(Constants.WORKFLOW_POD_LABEL)
            .list()
            .getItems());
    }

    /**
     * Creating pod.
     *
     * @param namespace namespace
     * @param pod       Pod
     */
    public void createPod(final String namespace, Pod pod) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespace)
            .createOrReplace(new PodBuilder(pod)
                                 .editMetadata()
                                 .addToLabels(K8sUtils.APP, appNameLabel)
                                 .endMetadata()
                                 .build()));

    }

    /**
     * Creating service account.
     *
     * @param namespace      namespace
     * @param serviceAccount ServiceAccount
     */
    public void createServiceAccount(final String namespace, ServiceAccount serviceAccount) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .serviceAccounts()
            .inNamespace(namespace)
            .createOrReplace(serviceAccount));

    }

    /**
     * Get service account.
     *
     * @param namespace namespace
     * @param name      ServiceAccount name
     */
    public ServiceAccount getServiceAccount(final String namespace, String name) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .serviceAccounts()
            .inNamespace(namespace)
            .withName(name)
            .require());
    }

    /**
     * Stopping pod.
     *
     * @param namespace namespace
     * @param name      pod name
     */
    public void stopPod(final String namespace, final String name) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespace)
            .withName(name)
            .inContainer(K8sUtils.JOB_CONTAINER)
            .redirectingInput()
            .redirectingOutput()
            .withTTY()
            .exec(POD_STOP_COMMAND.split(" "))).close();
    }

    /**
     * Deleting pod by name.
     *
     * @param namespace namespace
     * @param name      pod name
     */
    public void deletePod(final String namespace, final String name) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespace)
            .withName(name)
            .withGracePeriod(0)
            .delete());
    }

    /**
     * Deleting pod by labels.
     *
     * @param namespace namespace
     * @param labels    labels if they exists
     */
    public void deletePodsByLabels(final String namespace, final Map<String, String> labels) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespace)
            .withLabels(labels)
            .withGracePeriod(0)
            .delete());
    }

    /**
     * Checks whether user can update given resource.
     *
     * @param namespace namespace.
     * @param resource  resource kind.
     * @param group     resource group.
     * @param action    allowed action.
     * @return true if resource is accessible.
     */
    public boolean isAccessible(
        final String namespace, final String resource, final String group, final String action) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .authorization()
            .v1()
            .selfSubjectAccessReview()
            .create(new SelfSubjectAccessReviewBuilder()
                        .editOrNewSpec()
                        .editOrNewResourceAttributes()
                        .withGroup(group)
                        .withNamespace(namespace)
                        .withVerb(action)
                        .withResource(resource)
                        .endResourceAttributes()
                        .endSpec()
                        .build())
            .getStatus()
            .getAllowed());
    }

    /**
     * Checks whether namespace is available for user.
     *
     * @param resource namespace.
     * @return true if user can view namespace.
     */
    public boolean isViewable(final Namespace resource) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .authorization()
            .v1()
            .selfSubjectAccessReview()
            .create(new SelfSubjectAccessReviewBuilder()
                        .editOrNewSpec()
                        .editOrNewResourceAttributes()
                        .withNamespace(resource.getMetadata().getName())
                        .withVerb("get")
                        .withResource("namespaces")
                        .endResourceAttributes()
                        .endSpec()
                        .build())
            .getStatus()
            .getAllowed());
    }

    /**
     * Create or replace configMap.
     *
     * @param namespaceId namespace id
     * @param configMap   new configMap
     */
    public void createOrReplaceConfigMap(final String namespaceId, final ConfigMap configMap) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .configMaps()
            .inNamespace(namespaceId)
            .createOrReplace(new ConfigMapBuilder(configMap)
                                 .editMetadata()
                                 .addToLabels(K8sUtils.APP, appNameLabel)
                                 .endMetadata()
                                 .build()));
    }

    /**
     * Getting all config maps in namespace.
     *
     * @param namespaceId namespace id
     * @return List with all config maps
     */
    public List<ConfigMap> getAllConfigMaps(final String namespaceId) {
        return authenticatedCall(authenticationClient -> authenticationClient
            .configMaps()
            .inNamespace(namespaceId)
            .withLabel(Constants.TYPE, Constants.TYPE_JOB)
            .list()
            .getItems());
    }

    /**
     * Getting configmap by name.
     *
     * @param namespaceId namespace name
     * @param name        configmap name
     * @return configmap
     */
    public ConfigMap getConfigMap(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .configMaps()
            .inNamespace(namespaceId)
            .withName(name)
            .require());
    }

    /**
     * Getting configmaps by labels.
     *
     * @param namespaceId namespace name
     * @param labels      map of labels
     * @return configmap
     */
    public List<ConfigMap> getConfigMapsByLabels(final String namespaceId, final Map<String, String> labels) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .configMaps()
            .inNamespace(namespaceId)
            .withLabels(labels)
            .list()
            .getItems());
    }

    /**
     * Delete configmap by name.
     *
     * @param namespaceId namespace name
     * @param name        configmap name
     */
    public void deleteConfigMap(final String namespaceId, final String name) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .configMaps()
            .inNamespace(namespaceId)
            .withName(name)
            .delete());
    }

    /**
     * Deletes all config maps based on label values
     *
     * @param namespace k8s namespace
     * @param labels    labels
     */
    public void deleteConfigMapsByLabels(final String namespace, final Map<String, String> labels) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .configMaps()
            .inNamespace(namespace)
            .withLabels(labels)
            .withGracePeriod(0)
            .delete());
    }

    /**
     * Deletes all secrets based on label values
     *
     * @param namespace k8s namespace
     * @param labels    labels
     */
    public void deleteSecretsByLabels(final String namespace, final Map<String, String> labels) {
        authenticatedCall(authenticatedClient -> authenticatedClient
            .secrets()
            .inNamespace(namespace)
            .withLabels(labels)
            .withGracePeriod(0)
            .delete());
    }

    /**
     * Getting pod logs.
     *
     * @param namespaceId namespace name
     * @param name        pod name
     * @return logs as a String
     */
    public String getPodLogs(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespaceId)
            .withName(name)
            .inContainer(K8sUtils.JOB_CONTAINER)
            .getLog());
    }

    /**
     * Getting pod logs by labels.
     *
     * @param namespaceId namespace name
     * @param labels      pod labels
     * @return logs as a String
     */
    public String getPodLogsByLabels(final String namespaceId, final Map<String, String> labels) {
        List<Pod> pods = authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespaceId)
            .withLabels(labels)
            .list()
            .getItems());
        if (pods == null || pods.isEmpty()) {
            throw new ResourceNotFoundException("Cannot find any pods by provided labels");
        }
        return getPodLogs(namespaceId, pods.get(0).getMetadata().getName());
    }

    /**
     * Getting job logs.
     *
     * @param projectId project id
     * @param id        job id
     * @return list of log objects
     */
    public List<LogDto> getParsedPodLogs(final String projectId, final String id) {
        return getParsedLogs(() -> getPodLogs(projectId, id));
    }

    /**
     * Getting pod status in namespace by name.
     *
     * @param namespaceId namespace id
     * @param name        pod name
     * @return pod
     */
    public PodStatus getPodStatus(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespaceId)
            .withName(name)
            .require()).getStatus();
    }

    /**
     * Upload local file into container.
     *
     * @param namespace      namespace
     * @param uploadFilePath file upload path
     * @param podName        pod name for upload
     * @param multipartFile  multipart file
     */
    public void uploadFile(
            final String namespace,
            final String uploadFilePath,
            final String podName,
            final MultipartFile multipartFile
    ) {
        String fileName = multipartFile.getOriginalFilename();
        File file = new File(System.getProperty("java.io.tmpdir") + "/" + fileName);
        try {
            multipartFile.transferTo(file);
        } catch (IOException e) {
            throw new BadRequestException("Couldn't transfer multipart file type to file", e);
        }
        client
                .pods()
                .inNamespace(namespace)
                .withName(podName)
                .file(pvcMountPath + uploadFilePath)
                .upload(file.toPath());
    }

    /**
     * Download file from container.
     *
     * @param namespace         namespace
     * @param downloadFilePath  file download path
     * @param podName           pod name
     * @return                  array of bytes
     */
    public byte[] downloadFile(
            final String namespace,
            final String downloadFilePath,
            final String podName
    ) {
        byte[] result;
        try (InputStream is = client
                .pods()
                .inNamespace(namespace)
                .withName(podName)
                .file(pvcMountPath + downloadFilePath)
                .read()
        ) {
            result = is.readAllBytes();
            if(result.length == 0) {
                throw new BadRequestException("Couldn't find the file " + downloadFilePath);
            }
            return result;
        } catch (IOException e) {
            throw new BadRequestException("Couldn't read the file input stream from container", e);
        }
    }

    /**
     * Creates or updates PVC.
     *
     * @param namespace             name of namespace.
     * @param persistentVolumeClaim persistent volume claim.
     */
    public void createPVC(final String namespace, final PersistentVolumeClaim persistentVolumeClaim) {
        authenticatedCall(authenticatedClient -> authenticatedClient
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .create(persistentVolumeClaim));
    }

    /**
     * Monitors pod's events in namespace.
     *
     * @param namespace namespace
     * @param podName   podName
     * @param latch     latch
     * @return Watch
     */
    public Watch watchPod(final String namespace, final String podName, final CountDownLatch latch) {
        return client.pods().inNamespace(namespace).withName(podName).watch(new PodWatcher(podEventRepository, latch));
    }
}

@Slf4j
class PodWatcher implements Watcher<Pod> {
    private final PodEventRepositoryImpl podEventRepository;
    private final CountDownLatch latch;

    public PodWatcher(final PodEventRepositoryImpl podEventRepository,
                      final CountDownLatch latch) {
        this.podEventRepository = podEventRepository;
        this.latch = latch;
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
        if (pod.getStatus().getPhase().equals(K8sUtils.FAILED_STATUS) ||
                pod.getStatus().getPhase().equals(K8sUtils.SUCCEEDED_STATUS)) {
            PodEvent podEvent = new PodEvent(
                pod.getMetadata().getName(),
                pod.getMetadata().getLabels().get(Constants.TYPE),
                pod.getStatus().getStartTime(),
                K8sUtils.extractTerminatedStateField(pod.getStatus(), ContainerStateTerminated::getFinishedAt),
                pod.getMetadata().getLabels().get(Constants.STARTED_BY),
                pod.getStatus().getPhase());
            String key = pod.getMetadata().getNamespace() + "_" + pod.getMetadata().getName();
            podEventRepository.add(key, podEvent);
            LOGGER.info("Pod event successfully saved: {}", podEvent);
            latch.countDown();
        }
    }

    @Override
    public void onClose(WatcherException e) {
        if (e != null) {
            LOGGER.error("Watch error received: {}", e.getMessage(), e);
            latch.countDown();
        }
    }
}
