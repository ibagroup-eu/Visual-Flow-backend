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

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ResourceQuotaRequestDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ConfigMapListBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.NamespaceListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretListBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountList;
import io.fabric8.kubernetes.api.model.ServiceAccountListBuilder;
import io.fabric8.kubernetes.api.model.authorization.v1.SubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SubjectAccessReviewBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleList;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleListBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingList;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KubernetesServiceTest {

    private static final String APP_NAME = "vf";
    private static final String APP_NAME_LABEL = "testApp";

    private final KubernetesServer server = new KubernetesServer();

    @Mock
    private AuthenticationService authenticationServiceMock;

    private KubernetesService kubernetesService;

    @BeforeEach
    void setUp() {
        server.before();
        kubernetesService =
            new KubernetesService(server.getClient(), APP_NAME, APP_NAME_LABEL, authenticationServiceMock);
    }

    @AfterEach
    void tearDown() {
        server.after();
    }

    private void mockAuthenticationService() {
        UserInfo ui = new UserInfo();
        ui.setSuperuser(true);
        when(authenticationServiceMock.getUserInfo()).thenReturn(ui);
    }

    @Test
    void testGetNamespaces() {
        NamespaceList namespaceList = new NamespaceListBuilder()
            .addNewItem()
            .withNewMetadata()
            .withName("vf-name1")
            .endMetadata()
            .endItem()
            .addNewItem()
            .withNewMetadata()
            .withName("vf-name2")
            .endMetadata()
            .endItem()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces?labelSelector=app%3D" + APP_NAME_LABEL)
            .andReturn(HttpURLConnection.HTTP_OK, namespaceList)
            .once();

        List<Namespace> actual = kubernetesService.getNamespaces();

        assertEquals(namespaceList.getItems(), actual, "Namespaces must be equal to expected");
    }

    @Test
    void testGetResourceQuota() {
        mockAuthenticationService();

        String namespace = "namespace";

        ResourceQuota expected = new ResourceQuotaBuilder()
            .withNewMetadata()
            .withNamespace(namespace)
            .withName(Constants.QUOTA_NAME)
            .endMetadata()
            .withNewSpec()
            .withHard(Map.of("cpu", Quantity.parse("20"), "memory", Quantity.parse("20G")))
            .endSpec()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/namespace/resourcequotas/quota")
            .andReturn(HttpURLConnection.HTTP_OK, expected)
            .once();

        ResourceQuota result = kubernetesService.getResourceQuota(namespace, Constants.QUOTA_NAME);

        assertEquals(expected, result, "ResourceQuota must be equals to expected");
    }

    @Test
    void testCreateOrUpdateResourceQuota() {
        mockAuthenticationService();

        String namespace = "namespace";

        ResourceQuotaRequestDto quotaDto = ResourceQuotaRequestDto
            .builder()
            .limitsCpu(20f)
            .limitsMemory(20f)
            .requestsCpu(20f)
            .requestsMemory(20f)
            .build();

        server
            .expect()
            .post()
            .withPath("/api/v1/namespaces/namespace/resourcequotas")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .once();

        kubernetesService.createOrReplaceResourceQuota(namespace, quotaDto.toResourceQuota().build());
    }

    @Test
    void testCreateNamespace() {
        mockAuthenticationService();

        ProjectRequestDto projectDto = ProjectRequestDto.builder().name("Namespace").description("").build();

        server
            .expect()
            .post()
            .withPath("/api/v1/namespaces")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .once();

        kubernetesService.createNamespace(projectDto.toNamespace("namespace", Map.of()).build());
    }

    @Test
    void testEditDescription() {
        mockAuthenticationService();

        String namespace = "namespace";
        String description = "newDescription";

        ProjectRequestDto projectDto = ProjectRequestDto.builder().name("Namespace").description("").build();
        Namespace ns = projectDto.toNamespace("namespace", Map.of()).build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/namespace")
            .andReturn(HttpURLConnection.HTTP_OK, ns)
            .once();
        server
            .expect()
            .patch()
            .withPath("/api/v1/namespaces/namespace")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        kubernetesService.editDescription(namespace, description);
    }

    @Test
    void testGetParams() {
        mockAuthenticationService();

        String namespace = "namespace";
        Secret expected = ParamsDto
            .builder()
            .params(List.of(ParamDto.builder().key("test").value("val").secret(false).build()))
            .build()
            .toSecret()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/namespace/secrets/secret")
            .andReturn(HttpURLConnection.HTTP_OK, expected)
            .once();

        Secret result = kubernetesService.getSecret(namespace, ParamsDto.SECRET_NAME);
        assertEquals(expected, result, "Secret must be equals to expected");
    }

    @Test
    void testCreateOrUpdateSecret() {
        mockAuthenticationService();

        String namespace = "namespace";
        Secret secret = ParamsDto
            .builder()
            .params(List.of(ParamDto.builder().key("test").value("val").secret(false).build()))
            .build()
            .toSecret()
            .build();

        server
            .expect()
            .post()
            .withPath("/api/v1/namespaces/namespace/secrets")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .once();

        kubernetesService.createOrReplaceSecret(namespace, secret);
    }

    @Test
    void testIsSecretExist() {
        mockAuthenticationService();

        String namespace = "namespace";
        Secret secret = ParamsDto
                .builder()
                .params(List.of(ParamDto.builder().key("test").value("val").secret(false).build()))
                .build()
                .toSecret()
                .build();
        boolean expected = true;

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/secrets/secret")
                .andReturn(HttpURLConnection.HTTP_OK, secret)
                .once();

        boolean result = kubernetesService.isSecretExist(namespace, "secret");
        assertEquals(expected, result, "Secret must be equals to expected");
    }

    @Test
    void testGetNamespaceByName() {
        mockAuthenticationService();

        ProjectRequestDto projectDto = ProjectRequestDto.builder().name("Project").description("desc").build();
        Namespace expected = projectDto.toNamespace("project", Map.of()).build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/project")
            .andReturn(HttpURLConnection.HTTP_OK, expected)
            .once();

        Namespace namespaceByName = kubernetesService.getNamespace(expected.getMetadata().getName());

        assertEquals(expected, namespaceByName, "Namespace must be equals to expected");
    }

    @Test
    void testDeleteNamespace() {
        mockAuthenticationService();
        String namespaceName = "namespaceName";

        server
            .expect()
            .delete()
            .withPath("/api/v1/namespaces/namespaceName")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        kubernetesService.deleteNamespace(namespaceName);
    }

    @Test
    void testGetRoles() {
        ClusterRoleList expected = new ClusterRoleListBuilder()
            .addNewItem()
            .withNewMetadata()
            .withName("admin")
            .addToLabels("vf-role", "true")
            .endMetadata()
            .endItem()
            .build();

        server
            .expect()
            .get()
            .withPath("/apis/rbac.authorization.k8s.io/v1/clusterroles?labelSelector=vf-role%3Dtrue")
            .andReturn(HttpURLConnection.HTTP_OK, expected)
            .once();

        List<ClusterRole> actual = kubernetesService.getRoles();
        assertEquals(expected.getItems(), actual, "Roles must be equal to expected");
    }

    @Test
    void testGetServiceAccounts() {
        ServiceAccountList expected = new ServiceAccountListBuilder()
            .addNewItem()
            .withNewMetadata()
            .withName("ivanshautsou")
            .addToLabels("app", APP_NAME_LABEL)
            .addToAnnotations(Map.of("username", "IvanShautsou", "id", "22", "name", "Ivan"))
            .endMetadata()
            .endItem()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/serviceaccounts?labelSelector=app%3D"+ APP_NAME_LABEL)
            .andReturn(HttpURLConnection.HTTP_OK, expected)
            .once();

        List<ServiceAccount> actual = kubernetesService.getServiceAccounts();
        assertEquals(expected.getItems(), actual, "ServiceAccounts must be equal to expected");
    }

    @Test
    void testGetRoleBindings() {
        mockAuthenticationService();

        RoleBindingList expected = new RoleBindingListBuilder()
            .addNewItem()
            .withNewMetadata()
            .withName("ivanshautsou-admin")
            .addToAnnotations("username", "IvanShautsou")
            .endMetadata()
            .withNewRoleRef()
            .withName("admin")
            .endRoleRef()
            .endItem()
            .build();

        server
            .expect()
            .get()
            .withPath("/apis/rbac.authorization.k8s.io/v1/namespaces/name/rolebindings?labelSelector=app%3D" + APP_NAME_LABEL)
            .andReturn(HttpURLConnection.HTTP_OK, expected)
            .once();

        List<RoleBinding> actual = kubernetesService.getRoleBindings("name");
        assertEquals(expected.getItems(), actual, "RoleBindings must be equal to expected");
    }

    @Test
    void testDeleteRoleBindings() {
        mockAuthenticationService();

        RoleBindingList expected = new RoleBindingListBuilder()
            .addNewItem()
            .withNewMetadata()
            .withName("ivanshautsou-admin")
            .addToAnnotations("username", "IvanShautsou")
            .endMetadata()
            .withNewRoleRef()
            .withName("admin")
            .endRoleRef()
            .endItem()
            .build();

        server
            .expect()
            .delete()
            .withPath("/apis/rbac.authorization.k8s.io/v1/namespaces/name/rolebindings/ivanshautsou-admin")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        kubernetesService.deleteRoleBindings("name", expected.getItems());
    }

    @Test
    void testCreateOrReplaceServiceAccount() {
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername("TestUser");
        userInfo.setId("22");
        userInfo.setName("Test");

        ServiceAccountList expected1 = new ServiceAccountListBuilder()
            .addNewItem()
            .withNewMetadata()
            .withName("testuser")
            .addToLabels("app", APP_NAME_LABEL)
            .addToAnnotations(Map.of("username", "TestUser", "id", "22", "name", "Test"))
            .endMetadata()
            .endItem()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/serviceaccounts/testuser")
            .andReturn(HttpURLConnection.HTTP_OK, expected1)
            .once();

        kubernetesService.createIfNotExistServiceAccount(userInfo);

        ServiceAccount sa = new ServiceAccountBuilder()
                .withMetadata(new ObjectMetaBuilder().withName("sa").build())
                .addNewSecret().withName("user-token-secret").endSecret()
                .addNewSecret().withName("user-dockercfg-secret").endSecret()
                .build();
        ServiceAccountList expected2 = new ServiceAccountListBuilder()
            .withItems(sa)
            .withNewMetadata()
            .withResourceVersion("1")
            .endMetadata()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/serviceaccounts/testuser")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        server
            .expect()
            .post()
            .withPath("/api/v1/namespaces/vf/serviceaccounts")
            .andReturn(HttpURLConnection.HTTP_CREATED, sa)
            .once();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/serviceaccounts?fieldSelector=metadata.name%3Dtestuser&watch=false")
            .andReturn(HttpURLConnection.HTTP_OK, expected2)
            .once();

        kubernetesService.createIfNotExistServiceAccount(userInfo);
    }

    @Test
    void testCreateOrReplaceRoleBindings() {
        mockAuthenticationService();

        RoleBinding expected1 = new RoleBindingBuilder()
            .editOrNewMetadata()
            .addToAnnotations("username", "IvanShautsou")
            .withName("ivanshautsou-admin")
            .addToLabels("app", APP_NAME_LABEL)
            .endMetadata()
            .addNewSubject()
            .withKind("ServiceAccount")
            .withName("ivanshautsou")
            .withNamespace("vf")
            .endSubject()
            .withNewRoleRef()
            .withApiGroup("rbac.authorization.k8s.io")
            .withKind("Role")
            .withName("admin")
            .endRoleRef()
            .build();
        RoleBinding expected2 = new RoleBindingBuilder()
            .editOrNewMetadata()
            .addToAnnotations("username", "AKachkan")
            .withName("akachkan-admin")
            .addToLabels("app", APP_NAME_LABEL)
            .endMetadata()
            .addNewSubject()
            .withKind("ServiceAccount")
            .withName("akachkan")
            .withNamespace("vf")
            .endSubject()
            .withNewRoleRef()
            .withApiGroup("rbac.authorization.k8s.io")
            .withKind("Role")
            .withName("admin")
            .endRoleRef()
            .build();

        server
            .expect()
            .post()
            .withPath("/apis/rbac.authorization.k8s.io/v1/namespaces/name1/rolebindings")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .times(2);

        kubernetesService.createRoleBindings("name1", List.of(expected1, expected2));
    }

    @Test
    void testGetServiceAccountSecret() {
        String username = "IvanShautsou";

        ServiceAccount sa =
            new ServiceAccountBuilder().addNewSecret().withName("user-secret-token").endSecret().build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/serviceaccounts/ivanshautsou")
            .andReturn(HttpURLConnection.HTTP_OK, sa)
            .once();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/secrets/user-secret-token")
            .andReturn(HttpURLConnection.HTTP_OK, Optional.empty())
            .once();

        kubernetesService.getServiceAccountSecret(username);
    }

    @Test
    void testIsEditable() {
        mockAuthenticationService();

        SubjectAccessReview sa =
            new SubjectAccessReviewBuilder().withNewStatus().withAllowed(true).endStatus().build();

        server
            .expect()
            .post()
            .withPath("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
            .andReturn(HttpURLConnection.HTTP_OK, sa)
            .once();

        boolean result = kubernetesService.isAccessible("name1", "namespaces", "", "");
        assertTrue(result, "Must be true");
    }

    @Test
    void testTopPods() {
        List<PodMetrics> metrics = List.of(new PodMetricsBuilder()
                                               .addToContainers(new ContainerMetricsBuilder()
                                                                    .addToUsage(Constants.CPU_FIELD,
                                                                                Quantity.parse("10"))
                                                                    .addToUsage(Constants.MEMORY_FIELD,
                                                                                Quantity.parse("50Gi"))
                                                                    .build())
                                               .build());

        server
            .expect()
            .get()
            .withPath("/apis/metrics.k8s.io/v1beta1/namespaces/name1/pods")
            .andReturn(HttpURLConnection.HTTP_OK, new PodMetricsListBuilder().addAllToItems(metrics).build())
            .once();

        List<PodMetrics> result = kubernetesService.topPod("name1");
        assertEquals(metrics, result, "Pods must be equal to expected");
    }

    @Test
    void testTopPod() {
        PodMetrics metrics = new PodMetricsBuilder()

            .addToContainers(new ContainerMetricsBuilder()
                                 .addToUsage(Constants.CPU_FIELD, Quantity.parse("10"))
                                 .addToUsage(Constants.MEMORY_FIELD, Quantity.parse("50Gi"))
                                 .build()).build();

        server
            .expect()
            .get()
            .withPath("/apis/metrics.k8s.io/v1beta1/namespaces/name1/pods/id1")
            .andReturn(HttpURLConnection.HTTP_OK, metrics)
            .once();

        PodMetrics result = kubernetesService.topPod("name1", "id1");
        assertEquals(metrics, result, "Pod must be equals to expected");
    }

    @Test
    void testIsViewable() {
        mockAuthenticationService();

        SubjectAccessReview sa =
            new SubjectAccessReviewBuilder().withNewStatus().withAllowed(true).endStatus().build();

        server
            .expect()
            .post()
            .withPath("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
            .andReturn(HttpURLConnection.HTTP_OK, sa)
            .once();

        boolean result = kubernetesService.isViewable(new NamespaceBuilder()
                                                          .withNewMetadata()
                                                          .withName("name1")
                                                          .endMetadata()
                                                          .build());
        assertTrue(result, "Must be true");
    }

    @Test
    void testCreateOrReplaceConfigMap() {
        mockAuthenticationService();

        ConfigMap configMap = new ConfigMapBuilder()
            .addToData(Map.of("key", "data"))
            .withMetadata(new ObjectMetaBuilder()
                              .withName("id")
                              .addToLabels(Constants.NAME, "name")
                              .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                              .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String("data".getBytes()))
                              .build())
            .build();

        server
            .expect()
            .post()
            .withPath("/api/v1/namespaces/vf/configmaps")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .once();

        kubernetesService.createOrReplaceConfigMap("vf", configMap);
    }

    @Test
    void testDeletePod() {
        mockAuthenticationService();

        server
            .expect()
            .delete()
            .withPath("/api/v1/namespaces/vf/pods/pod")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        kubernetesService.deletePod("vf", "pod");
    }

    @Test
    void testDeletePodsByLabels() {
        mockAuthenticationService();

        server
            .expect()
            .delete()
            .withPath("/api/v1/namespaces/vf/pods?labelSelector=type%pod")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        kubernetesService.deletePodsByLabels("vf", Map.of("type", "pod"));
    }

    @Test
    void testGetAllConfigMaps() {
        mockAuthenticationService();

        ConfigMapList configMapList = new ConfigMapListBuilder()
            .addNewItem()
            .withNewMetadata()
            .withName("cm1")
            .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
            .endMetadata()
            .endItem()
            .addNewItem()
            .withNewMetadata()
            .withName("cm2")
            .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
            .endMetadata()
            .endItem()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/configmaps?labelSelector=type%3Djob")
            .andReturn(HttpURLConnection.HTTP_OK, configMapList)
            .once();

        List<ConfigMap> actual = kubernetesService.getAllConfigMaps("vf");

        assertEquals(configMapList.getItems(), actual, "ConfigMaps must be equal to expected");
    }

    @Test
    void testGetConfigMap() {
        mockAuthenticationService();
        ConfigMap configMap = new ConfigMapBuilder()
            .withNewMetadata()
            .withName("cm1")
            .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
            .endMetadata()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/configmaps/cm1")
            .andReturn(HttpURLConnection.HTTP_OK, configMap)
            .once();

        ConfigMap actual = kubernetesService.getConfigMap("vf", "cm1");

        assertEquals(configMap, actual, "ConfigMap must be equals to expected");
    }

    @Test
    void testGetConfigMapsByLabels() {
        mockAuthenticationService();
        ConfigMapList configMapList = new ConfigMapListBuilder()
            .addNewItem()
            .withMetadata(new ObjectMetaBuilder()
                              .withName("cm1")
                              .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                              .addToLabels(Constants.NAME, "name1")
                              .build())
            .endItem()
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/configmaps?labelSelector=name%3Dname1")
            .andReturn(HttpURLConnection.HTTP_OK, configMapList)
            .once();

        List<ConfigMap> actual = kubernetesService.getConfigMapsByLabels("vf", Map.of(Constants.NAME, "name1"));

        assertEquals(configMapList.getItems().get(0), actual.get(0), "ConfigMaps must be equal to expected");
    }

    @Test
    void testGetPodsByLabels() {
        mockAuthenticationService();
        Pod pod = new PodBuilder()
            .withMetadata(new ObjectMetaBuilder()
                              .withName("cm1")
                              .addToLabels(Constants.JOB_ID_LABEL, "name1")
                              .build())
            .build();

        server
            .expect()
            .get()
            .withPath("/api/v1/namespaces/vf/pods?labelSelector=jobId%3Dname1")
            .andReturn(HttpURLConnection.HTTP_OK, new PodListBuilder().addToItems(pod).build())
            .once();

        List<Pod> actual = kubernetesService.getPodsByLabels("vf", Map.of(Constants.JOB_ID_LABEL, "name1"));

        assertEquals(pod, actual.get(0), "Pod must be equals to expected");
    }

    @Test
    void testDeleteConfigMap() {
        mockAuthenticationService();

        server
            .expect()
            .delete()
            .withPath("/api/v1/namespaces/namespaceName/configmaps?labelSelector=id%3Dcm1")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        kubernetesService.deleteConfigMap("namespaceName", "cm1");
    }

    @Test
    void testCreateRoleBinding() {
        server
            .expect()
            .post()
            .withPath("/apis/rbac.authorization.k8s.io/v1beta1/namespaces/vf/rolebindings")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        RoleBinding rb = new RoleBindingBuilder().build();

        kubernetesService.createRoleBinding("vf", rb);
    }

    @Test
    void testGetRoleBinding() {
        RoleBinding rb = new RoleBindingBuilder().build();

        server
            .expect()
            .get()
            .withPath("/apis/rbac.authorization.k8s.io/v1beta1/namespaces/vf/rolebindings/id")
            .andReturn(HttpURLConnection.HTTP_OK, rb)
            .once();

        kubernetesService.getRoleBinding("vf", "id");
    }

    @Test
    void testGetSecretsByLabels() {
        mockAuthenticationService();

        String namespace = "namespace";
        Secret secret = new SecretBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName("secret1")
                        .addToLabels(Constants.JOB_ID_LABEL, "name1")
                        .build())
                .build();

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/secrets?labelSelector=jobId%3Dname1")
                .andReturn(HttpURLConnection.HTTP_OK, new SecretListBuilder().addToItems(secret).build())
                .once();

        List<Secret> result = kubernetesService.getSecretsByLabels(namespace, Map.of(Constants.JOB_ID_LABEL, "name1"));
        assertEquals(secret, result.get(0), "Secret must be equals to expected");
    }

    @Test
    void testGetServiceAccount() {
        mockAuthenticationService();

        String namespace = "namespace";
        String name = "serviceAccount";
        ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .build())
                .build();

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/serviceaccounts/serviceAccount")
                .andReturn(HttpURLConnection.HTTP_OK, new ServiceAccountBuilder(serviceAccount).build())
                .once();

        ServiceAccount result = kubernetesService.getServiceAccount(namespace, name);
        assertEquals(serviceAccount, result, "Secret must be equals to expected");
    }

    @Test
    void testGetPodStatus() {
        mockAuthenticationService();
        String namespace = "namespace";
        String name = "pod1";
        Pod pod = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .build())
                .build();
        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/pods/pod1")
                .andReturn(HttpURLConnection.HTTP_OK, new PodBuilder(pod).build())
                .once();

        PodStatus result = kubernetesService.getPodStatus(namespace, name);
        assertEquals(pod.getStatus(), result, "Pod must be equals to expected");
    }

    @Test
    void testDeleteSecretsByLabels() {
        mockAuthenticationService();

        String namespace = "namespace";
        server
                .expect()
                .delete()
                .withPath("/api/v1/namespaces/namespace/secrets?labelSelector=jobId%3Dname1")
                .andReturn(HttpURLConnection.HTTP_OK, null)
                .once();

        kubernetesService.deleteSecretsByLabels(namespace, Map.of(Constants.JOB_ID_LABEL, "name1"));
    }

    @Test
    void testDeleteConfigMapsByLabels() {
        mockAuthenticationService();

        String namespace = "namespace";
        server
                .expect()
                .delete()
                .withPath("/api/v1/namespaces/namespace/configmaps?labelSelector=jobId%3Dname1")
                .andReturn(HttpURLConnection.HTTP_OK, null)
                .once();

        kubernetesService.deleteConfigMapsByLabels(namespace, Map.of(Constants.JOB_ID_LABEL, "name1"));
    }

    @Test
    void testCreatePod() {
        mockAuthenticationService();

        Pod pod = new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName("pod1")
                        .addToLabels(K8sUtils.APP, "vf-dev")
                        .build())
                .build();

        server
                .expect()
                .post()
                .withPath("/api/v1/namespaces/namespace/pods")
                .andReturn(HttpURLConnection.HTTP_OK, null)
                .once();

        kubernetesService.createPod("namespace", pod);
    }

    @Test
    void testCreateServiceAccount() {
        mockAuthenticationService();

        ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withMetadata(new ObjectMetaBuilder()
                    .withName("sa")
                    .build())
                .build();

        server
                .expect()
                .post()
                .withPath("/api/v1/namespaces/namespace/serviceaccounts")
                .andReturn(HttpURLConnection.HTTP_OK, null)
                .once();

        kubernetesService.createServiceAccount("namespace", serviceAccount);
    }

    @Test
    void testGetUniqueEntityName() {
        String name = UUID.randomUUID().toString();
        AtomicBoolean firstTime = new AtomicBoolean(true);
        String actual = KubernetesService.getUniqueEntityName((String secName) -> {
            if (firstTime.get()) {
                firstTime.set(false);
                return name;
            } else {
                return null;
            }
        });
        assertNotEquals(name, actual, "Names should be different");
    }
}