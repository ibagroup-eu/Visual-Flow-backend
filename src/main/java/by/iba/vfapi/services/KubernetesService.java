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

import by.iba.vfapi.common.LoadFilePodBuilderService;
import by.iba.vfapi.config.ApplicationConfigurationProperties;
import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dao.LogRepositoryImpl;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.projects.DemoLimitsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.services.utils.AccessTableUtils;
import by.iba.vfapi.services.utils.K8sUtils;
import by.iba.vfapi.services.watchers.PodWatcher;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
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
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.FunctionCallable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * KubernetesService class.
 */
@Service
@Slf4j
@Getter
// This class contains a sonar vulnerability - java:S1200: Split this class into smaller and more specialized ones to
// reduce its dependencies on other classes from 33 to the maximum authorized 30 or less. This means, that this class
// should not be coupled to too many other classes (Single Responsibility Principle).
// This class also contains java:S1996: There are 2 top-level types in this file; move all but one of them to other
// files. This means, that files should contain only one top-level class or interface each.
// This class also contains java:S1448: This class has 53 methods, which is greater than the 35 authorized. Split it
// into smaller classes. This means that classes should not have too many methods.
public class KubernetesService {
    public static final String NO_POD_MESSAGE = "Pod doesn't exist";
    private static final String POD_STOP_COMMAND = "pkill -SIGTERM -u job-user";
    private static final String PATH_DELIMITER = "/";
    private static final String SERIALIZE_DS_ERR =
            "Cannot update demo project '{}', since demo limits have incorrect format: {}";
    private static final int SERVICE_ACCOUNT_WAIT_PERIOD_MIN = 2;
    private static final int POD_WAIT_PERIOD_MIN = 1;
    private final ApplicationConfigurationProperties appProperties;
    protected final NamespacedKubernetesClient client;
    protected final NamespacedKubernetesClient userClient;
    protected final AuthenticationService authenticationService;
    protected final LoadFilePodBuilderService filePodService;

    public KubernetesService(
        final ApplicationConfigurationProperties appProperties,
        final NamespacedKubernetesClient client,
        final AuthenticationService authenticationService,
        final LoadFilePodBuilderService filePodService) {
        this.appProperties = appProperties;
        this.authenticationService = authenticationService;
        this.client = client;
        this.filePodService = filePodService;
        this.userClient = new DefaultKubernetesClient(new ConfigBuilder(client.getConfiguration())
                                                          .withClientKeyFile(null)
                                                          .withClientCertData(null)
                                                          .withClientCertFile(null)
                                                          .withClientKeyData(null)
                                                          .build());
    }

    protected FunctionCallable<NamespacedKubernetesClient> getAuthenticatedClient(NamespacedKubernetesClient kbClient) {
        Secret secret = getServiceAccountSecret(authenticationService.getUserInfo()
                .map(UserInfo::getUsername)
                .orElseThrow(() -> new InsufficientAuthenticationException("Authentication required")));
        String k8sToken = new String(Base64.decodeBase64(secret.getData().get(Constants.TOKEN)),
                StandardCharsets.UTF_8);
        return kbClient.withRequestConfig(new RequestConfigBuilder().withOauthToken(k8sToken).build());
    }

    protected <T> T authenticatedCall(Function<NamespacedKubernetesClient, T> caller) {
        if (authenticationService.getUserInfo()
                .map(UserInfo::isSuperuser)
                .orElse(true)) {
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
                        .addToLabels(K8sUtils.APP, appProperties.getNamespace().getLabel())
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
        return client.namespaces().withLabel(K8sUtils.APP, appProperties.getNamespace().getLabel()).list().getItems();
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
     * Changes project's demo limits.
     *
     * @param namespace   name of namespace.
     * @param demoLimits new project demoLimits.
     */
    public void editDemoLimits(final String namespace, final boolean isDemo, @Valid final DemoLimitsDto demoLimits) {
        authenticatedCall(authenticatedClient -> authenticatedClient
                .namespaces()
                .withName(namespace)
                .edit((Namespace ns) -> {
                    try {
                        ObjectMetaBuilder newMeta = new ObjectMetaBuilder(ns.getMetadata())
                                .addToAnnotations(Constants.DEMO_FIELD, String.valueOf(isDemo));
                        if (demoLimits != null) {
                            newMeta
                                    .addToAnnotations(Constants.VALID_TO_FIELD,
                                            demoLimits.getExpirationDate().toString())
                                    .addToAnnotations(Constants.DATASOURCE_LIMIT, new ObjectMapper()
                                            .writeValueAsString(demoLimits.getSourcesToShow()))
                                    .addToAnnotations(Constants.JOBS_LIMIT,
                                            String.valueOf(demoLimits.getJobsNumAllowed()))
                                    .addToAnnotations(Constants.PIPELINES_LIMIT,
                                            String.valueOf(demoLimits.getPipelinesNumAllowed()));
                        }
                        ns.setMetadata(newMeta.build());
                    } catch (JacksonException e) {
                        LOGGER.error(SERIALIZE_DS_ERR, namespace, e.getLocalizedMessage());
                    }
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
                                 .addToLabels(K8sUtils.APP, appProperties.getNamespace().getLabel())
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
                                 .addToLabels(K8sUtils.APP, appProperties.getNamespace().getLabel())
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
     * Check if pod exists.
     *
     * @param namespace  name of namespace.
     * @param podName name of the secret.
     * @return secret.
     */
    public Boolean isPodExist(final String namespace, final String podName) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespace)
            .withName(podName)
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
                .addToLabels(K8sUtils.APP, appProperties.getNamespace().getLabel())
                .endMetadata()
                .editSubject(0)
                .withName(K8sUtils.getValidK8sName(roleBinding.getSubjects().get(0).getName()))
                .withNamespace(appProperties.getNamespace().getApp())
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
            .withLabel(K8sUtils.APP, appProperties.getNamespace().getLabel())
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
                                                 AccessTableUtils.USERNAME,
                                                 userInfo.getUsername(),
                                                 "name",
                                                 userInfo.getName(),
                                                 "email",
                                                 userInfo.getEmail());
        String saName = K8sUtils.getValidK8sName(userInfo.getUsername());

        if (client.serviceAccounts().inNamespace(appProperties.getNamespace().getApp()).withName(saName)
                .get() == null) {
            client
                .serviceAccounts()
                .inNamespace(appProperties.getNamespace().getApp())
                .createOrReplace(new ServiceAccountBuilder()
                    .withNewMetadata()
                    .addToAnnotations(annotations)
                    .withName(saName)
                    .addToLabels(K8sUtils.APP, appProperties.getNamespace().getLabel())
                    .endMetadata()
                    .build());
            LOGGER.info("{} - created a new service account - {}, waiting for it to get ready...",
                AuthenticationService.getFormattedUserInfo(userInfo),
                saName);

            client
                .serviceAccounts()
                .inNamespace(appProperties.getNamespace().getApp())
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
            .inNamespace(appProperties.getNamespace().getApp())
            .withName(K8sUtils.getValidK8sName(username))
            .require()
            .getSecrets()
            .stream()
            .filter(secret -> secret.getName().contains(Constants.TOKEN))
            .findFirst()
            .orElseThrow(() -> new InternalProcessingException("Unable to find service account secret for user: " +
                                                                   username))
            .getName();
        return client.secrets().inNamespace(appProperties.getNamespace().getApp()).withName(secretName).require();
    }

    /**
     * Retrieves service accounts.
     *
     * @return service account info.
     */
    public List<ServiceAccount> getServiceAccounts() {
        return client
            .serviceAccounts()
            .inNamespace(appProperties.getNamespace().getApp())
            .withLabel(K8sUtils.APP, appProperties.getNamespace().getLabel())
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
        return client
            .pods()
            .inNamespace(namespaceId)
            .withLabels(labels)
            .list()
            .getItems();
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
     * @param podBuilder       Pod
     * @return created Pod
     */
    public Pod createPod(final String namespace, PodBuilder podBuilder) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespace)
            .createOrReplace(podBuilder
                                 .editMetadata()
                                 .addToLabels(K8sUtils.APP, appProperties.getNamespace().getLabel())
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
    public boolean isViewable(final HasMetadata resource) {
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
                                 .addToLabels(K8sUtils.APP, appProperties.getNamespace().getLabel())
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
     * Determine, if config map is readable.
     *
     * @param namespaceId namespace name
     * @param name        configmap name
     * @return true - if config map is readable, otherwise - false.
     */
    public boolean isConfigMapReadable(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
                .configMaps()
                .inNamespace(namespaceId)
                .withName(name)
                .isReady());
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
     * Getting first configmap by label.
     *
     * @param namespaceId namespace name
     * @param labelKey label key
     * @param labelValue label value
     * @return configmap
     */
    @Nullable
    public ConfigMap getConfigMapByLabel(final String namespaceId, String labelKey, String labelValue) {
        ConfigMapList result = authenticatedCall(authenticatedClient -> authenticatedClient
                .configMaps()
                .inNamespace(namespaceId)
                .withLabel(labelKey, labelValue)
                .list());
        if (!result.getItems().isEmpty()) {
            return result.getItems().get(0);
        }
        return null;
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
     * Retrieves a Pod by its namespace and name.
     *
     * @param namespaceId the ID of the namespace where the Pod is located
     * @param name        the name of the Pod to retrieve
     * @return the Pod object
     */
    public Pod getPod(String namespaceId, String name) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
                .pods()
                .inNamespace(namespaceId)
                .withName(name)
                .require());
    }

    /**
     * Getting pod status in namespace by name.
     *
     * @param namespaceId namespace id
     * @param name        pod name
     * @return pod
     */
    public PodStatus getPodStatus(final String namespaceId, final String name) {
        return getPod(namespaceId, name).getStatus();
    }

    /**
     * Getting pod labels in namespace by name.
     *
     * @param namespaceId namespace id
     * @param name        pod name
     * @return pod labels
     */
    public Map<String, String> getPodLabels(final String namespaceId, final String name) {
        return client
                .pods()
                .inNamespace(namespaceId)
                .withName(name)
                .require()
                .getMetadata()
                .getLabels();
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
        File file = new File(System.getProperty("java.io.tmpdir") + PATH_DELIMITER + fileName);
        try {
            multipartFile.transferTo(file);
        } catch (IOException e) {
            throw new BadRequestException("Couldn't transfer multipart file type to file", e);
        }
        createIfNotExistPodForUploadDownloadFile(namespace, podName);
        client
            .pods()
            .inNamespace(namespace)
            .withName(podName)
            .file(appProperties.getPvc().getMountPath() + uploadFilePath)
            .upload(file.toPath());
        LOGGER.info("The file has uploaded successfully!");
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
        createIfNotExistPodForUploadDownloadFile(namespace, podName);
        byte[] result;
        try (InputStream is = client
                .pods()
                .inNamespace(namespace)
                .withName(podName)
                .file(appProperties.getPvc().getMountPath() + downloadFilePath)
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

    public void createIfNotExistPodForUploadDownloadFile(String namespace, String podName) {
        if(Boolean.FALSE.equals(isPodExist(namespace, podName))) {
            LOGGER.warn("Couldn't find container {} for upload local files to the cluster. " +
                    "Starting creating the container...", podName);
            createPod(namespace, filePodService.getLoadFilePod(namespace, filePodService.getBufferPVCPodParams(),
                    appProperties.getPvc().getMountPath(), appProperties.getJob().getImagePullSecret())
            );
            client
                    .pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .waitUntilReady(POD_WAIT_PERIOD_MIN, TimeUnit.MINUTES);
            LOGGER.info("The container {} has created successfully!", podName);
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
     * Getting pod logs.
     *
     * @param namespaceId namespace name
     * @param name        pod name
     * @return logs as a String
     */
    public String getPodLogs(final String namespaceId, final String name) {
        return client
                .pods()
                .inNamespace(namespaceId)
                .withName(name)
                .inContainer(K8sUtils.JOB_CONTAINER)
                .getLog();
    }

    /**
     * Monitors pod's events in namespace.
     *
     * @param namespace namespace
     * @param podName   podName
     * @param latch     latch
     * @return Watch
     */
    public Watch watchPod(final String namespace, final String podName,
                          final JobHistoryRepository historyRepository,
                          final LogRepositoryImpl logRepository ,
                          final CountDownLatch latch) {
        return authenticatedCall(authenticatedClient -> authenticatedClient
            .pods()
            .inNamespace(namespace)
            .withName(podName)
            .watch(new PodWatcher(historyRepository, logRepository, latch, authenticatedClient)));
    }
}
