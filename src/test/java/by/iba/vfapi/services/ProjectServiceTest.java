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

import by.iba.vfapi.config.CustomNamespaceAnnotationsConfig;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ProjectOverviewDto;
import by.iba.vfapi.dto.projects.ProjectOverviewListDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.dto.projects.ResourceQuotaRequestDto;
import by.iba.vfapi.dto.projects.ResourceQuotaResponseDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceStatusBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final String PREFIX = "vf-";
    private static final String PROJECT_NAME = "project name";
    private static final String PROJECT_ID = "vf-project-name";

    @Mock
    private KubernetesService kubernetesService;
    @Mock
    private AuthenticationService authenticationService;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(kubernetesService,
                                            PREFIX,
                                            "imagePullSecret",
                                            "spark",
                                            "spark-edit",
                                            "vf",
                                            new CustomNamespaceAnnotationsConfig(),
                                            authenticationService);
    }


    @Test
    void testCreate() {
        String description = "description";
        ResourceQuotaRequestDto quotaDto = ResourceQuotaRequestDto
            .builder()
            .limitsCpu(20f)
            .limitsMemory(20f)
            .requestsCpu(20f)
            .requestsMemory(20f)
            .build();
        ProjectRequestDto projectDto =
            ProjectRequestDto.builder().name(PROJECT_NAME).description(description).limits(quotaDto).build();
        ParamsDto paramsDto = ParamsDto.builder().build();
        Namespace ns =
            projectDto.toNamespace("id", Map.of()).editMetadata().withName("vf-project-name").endMetadata().build();
        ResourceQuota rq = quotaDto.toResourceQuota().build();
        Secret s = paramsDto.toSecret().build();
        doNothing().when(kubernetesService).createNamespace(ns);
        doNothing().when(kubernetesService).createOrReplaceResourceQuota(PROJECT_ID, rq);
        doNothing().when(kubernetesService).createOrReplaceSecret(PROJECT_ID, s);
        when(kubernetesService.getServiceAccount("vf", "spark")).thenReturn(new ServiceAccountBuilder()
                                                                                 .editOrNewMetadata()
                                                                                 .withName("spark")
                                                                                 .endMetadata()
                                                                                 .build());
        when(kubernetesService.getSecret("vf", "imagePullSecret")).thenReturn(new SecretBuilder()
                                                                                   .editOrNewMetadata()
                                                                                   .withName("imagePullSecret")
                                                                                   .endMetadata()
                                                                                   .build());
        when(kubernetesService.getRoleBinding("vf", "spark-edit")).thenReturn(new RoleBindingBuilder()
                                                                                   .editOrNewMetadata()
                                                                                   .withName("spark-edit")
                                                                                   .endMetadata()
                                                                                   .addNewSubject()
                                                                                   .withKind("ServiceAccount")
                                                                                   .withNamespace("vf")
                                                                                   .withName("spark-edit")
                                                                                   .endSubject()
                                                                                   .build());
        ServiceAccount expectedSa = new ServiceAccountBuilder()
            .editOrNewMetadata()
            .withName("spark")
            .withNamespace(PROJECT_ID)
            .endMetadata()
            .build();
        Secret expectedSecret = new SecretBuilder()
            .editOrNewMetadata()
            .withName("imagePullSecret")
            .withNamespace(PROJECT_ID)
            .endMetadata()
            .build();
        RoleBinding expectedRb = new RoleBindingBuilder()
            .editOrNewMetadata()
            .withName("spark-edit")
            .withNamespace(PROJECT_ID)
            .endMetadata()
            .addNewSubject()
            .withKind("ServiceAccount")
            .withNamespace(PROJECT_ID)
            .withName("spark-edit")
            .endSubject()
            .build();
        doNothing().when(kubernetesService).createServiceAccount(PROJECT_ID, expectedSa);
        doNothing().when(kubernetesService).createOrReplaceSecret(PROJECT_ID, expectedSecret);

        projectService.create(projectDto);

        verify(kubernetesService).createNamespace(ns);
        verify(kubernetesService).createOrReplaceResourceQuota(PROJECT_ID, rq);
        verify(kubernetesService).createOrReplaceSecret(PROJECT_ID, s);
        verify(kubernetesService).createServiceAccount(PROJECT_ID, expectedSa);
        verify(kubernetesService).createOrReplaceSecret(PROJECT_ID, expectedSecret);
        verify(kubernetesService).createRoleBinding(PROJECT_ID, expectedRb);
        verify(kubernetesService).getServiceAccount("vf", "spark");
    }

    @Test
    void testGet() {
        Map<String, Quantity> hard = Map.of(Constants.LIMITS_CPU,
                                            Quantity.parse("20"),
                                            Constants.LIMITS_MEMORY,
                                            Quantity.parse("100Gi"),
                                            Constants.REQUESTS_CPU,
                                            Quantity.parse("20"),
                                            Constants.REQUESTS_MEMORY,
                                            Quantity.parse("100Gi"));
        Map<String, Quantity> used = Map.of(Constants.LIMITS_CPU,
                                            Quantity.parse("2"),
                                            Constants.LIMITS_MEMORY,
                                            Quantity.parse("2Gi"),
                                            Constants.REQUESTS_CPU,
                                            Quantity.parse("1"),
                                            Constants.REQUESTS_MEMORY,
                                            Quantity.parse("1Gi"));
        Namespace namespace = new NamespaceBuilder()
            .withNewMetadata()
            .withName(PROJECT_ID)
            .addToAnnotations(Constants.DESCRIPTION_FIELD, "description")
            .endMetadata()
            .build();
        ResourceQuota quota =
            new ResourceQuotaBuilder().withNewStatus().addToHard(hard).addToUsed(used).endStatus().build();
        when(kubernetesService.getNamespace(PROJECT_ID)).thenReturn(namespace);
        when(kubernetesService.getResourceQuota(PROJECT_ID, Constants.QUOTA_NAME)).thenReturn(quota);
        when(kubernetesService.isAccessible(PROJECT_ID,
                                            "namespaces",
                                            "",
                                            Constants.UPDATE_ACTION)).thenReturn(true);

        ProjectResponseDto result = projectService.get(PROJECT_ID);

        assertEquals(ProjectResponseDto
                         .fromNamespace(namespace)
                         .limits(ResourceQuotaResponseDto.limitsFromResourceQuota(quota).build())
                         .usage(ResourceQuotaResponseDto.usageFromResourceQuota(quota).build())
                         .editable(true)
                         .build(), result, "Project must be equals to expected");
        verify(kubernetesService).getNamespace(PROJECT_ID);
        verify(kubernetesService).getResourceQuota(PROJECT_ID, Constants.QUOTA_NAME);
        verify(kubernetesService).isAccessible(PROJECT_ID, "namespaces", "", Constants.UPDATE_ACTION);
    }

    @Test
    void testGetAll() {
        Namespace namespace = new NamespaceBuilder()
            .withStatus(new NamespaceStatusBuilder().withPhase("Active").build())
            .withNewMetadata()
            .withName(PROJECT_ID)
            .addToAnnotations(Constants.DESCRIPTION_FIELD, "description")
            .endMetadata()
            .build();
        when(kubernetesService.getNamespaces()).thenReturn(List.of(namespace));
        when(kubernetesService.isViewable(namespace)).thenReturn(true);
        when(authenticationService.getUserInfo()).thenReturn(new UserInfo("id",
                                                                          "name",
                                                                          "username",
                                                                          "email",
                                                                          true));

        ProjectOverviewListDto result = projectService.getAll();


        assertEquals(ProjectOverviewListDto
                         .builder()
                         .projects(List.of(ProjectOverviewDto.fromNamespace(namespace, false)))
                         .editable(true)
                         .build(), result, "Project must be equals to expected");
        verify(kubernetesService).getNamespaces();
        verify(kubernetesService).isViewable(namespace);
        verify(authenticationService).getUserInfo();
    }

    @Test
    void testDelete() {
        doNothing().when(kubernetesService).deleteNamespace(PROJECT_ID);
        projectService.delete(PROJECT_ID);
        verify(kubernetesService).deleteNamespace(PROJECT_ID);
    }

    @Test
    void testGetUtilization() {
        Map<String, Quantity> hard = Map.of(Constants.LIMITS_CPU,
                                            Quantity.parse("20"),
                                            Constants.LIMITS_MEMORY,
                                            Quantity.parse("100Gi"),
                                            Constants.REQUESTS_CPU,
                                            Quantity.parse("20"),
                                            Constants.REQUESTS_MEMORY,
                                            Quantity.parse("100Gi"));
        ResourceQuota quota = new ResourceQuotaBuilder().withNewStatus().addToHard(hard).endStatus().build();
        List<PodMetrics> metrics = List.of(new PodMetricsBuilder()
                                               .addToContainers(new ContainerMetricsBuilder()
                                                                    .addToUsage(Constants.CPU_FIELD,
                                                                                Quantity.parse("10"))
                                                                    .addToUsage(Constants.MEMORY_FIELD,
                                                                                Quantity.parse("50Gi"))
                                                                    .build())
                                               .build());

        when(kubernetesService.getResourceQuota(PROJECT_ID, Constants.QUOTA_NAME)).thenReturn(quota);
        when(kubernetesService.topPod(PROJECT_ID)).thenReturn(metrics);

        ResourceUsageDto result = projectService.getUsage(PROJECT_ID);

        assertEquals(ResourceUsageDto.builder().cpu(0.500f).memory(0.500f).build(), result, "Utilization must be equals to expected");
        verify(kubernetesService).getResourceQuota(PROJECT_ID, Constants.QUOTA_NAME);
    }

    @Test
    void testUpdate() {
        ResourceQuotaRequestDto quotaDto = ResourceQuotaRequestDto
            .builder()
            .limitsCpu(20f)
            .limitsMemory(20f)
            .requestsCpu(20f)
            .requestsMemory(20f)
            .build();
        ResourceQuota rq = quotaDto.toResourceQuota().build();
        doNothing().when(kubernetesService).createOrReplaceResourceQuota(PROJECT_ID, rq);
        doNothing().when(kubernetesService).editDescription(PROJECT_ID, "newDesc");

        projectService.update(PROJECT_ID,
                              ProjectRequestDto.builder().limits(quotaDto).description("newDesc").build());

        verify(kubernetesService).createOrReplaceResourceQuota(PROJECT_ID, rq);
    }

    @Test
    void testUpdateParams() {
        List<ParamDto> params = List.of(ParamDto.builder().key("key1").value("val1").secret(Boolean.FALSE).build(),
                                        ParamDto.builder().key("key2").value("val2").secret(Boolean.TRUE).build());
        ParamsDto paramsDto = ParamsDto.builder().params(params).build();
        Secret secret = paramsDto.toSecret().build();
        doNothing().when(kubernetesService).createOrReplaceSecret(PROJECT_ID, secret);

        projectService.updateParams(PROJECT_ID, params);

        verify(kubernetesService).createOrReplaceSecret(PROJECT_ID, secret);
    }

    @Test
    void testGetParams() {
        Secret secret = new SecretBuilder()
            .addToData("key", "dmFsdWU=")
            .withNewMetadata()
            .withNamespace(PROJECT_ID)
            .addToAnnotations("key", "false")
            .endMetadata()
            .build();
        when(kubernetesService.getSecret(PROJECT_ID, ParamsDto.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION)).thenReturn(true);

        ParamsDto result = projectService.getParams(PROJECT_ID);

        assertEquals(ParamsDto
                         .builder()
                         .params(List.of(ParamDto
                                             .builder()
                                             .key("key")
                                             .value("value")
                                             .secret(Boolean.FALSE)
                                             .build()))
                         .editable(true)
                         .build(), result, "Params must be equal to expected");
        verify(kubernetesService).getSecret(PROJECT_ID, ParamsDto.SECRET_NAME);
        verify(kubernetesService).isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION);
    }

    @Test
    void testCreateAccessTable() {
        Map<String, String> accessTable = Map.of("Test", "Testing", "u1", "32");
        Map<String, String> accessTableToApply = Map.of("Test", "Testing");
        AccessTableDto accessTableDto = AccessTableDto.builder().grants(accessTable).build();
        AccessTableDto accessTableToApplyDto = AccessTableDto.builder().grants(accessTableToApply).build();
        List<RoleBinding> roleBindings = accessTableDto.toRoleBindings();
        List<RoleBinding> roleBindingsToApply = accessTableToApplyDto.toRoleBindings();
        List<RoleBinding> old = List.of(new RoleBindingBuilder()
                                            .withNewMetadata()
                                            .withName("u1-32")
                                            .addToAnnotations(AccessTableDto.USERNAME, "u1")
                                            .endMetadata()
                                            .withNewRoleRef()
                                            .withName("32")
                                            .endRoleRef()
                                            .build());
        doNothing().when(kubernetesService).createRoleBindings(PROJECT_ID, roleBindingsToApply);
        doNothing().when(kubernetesService).deleteRoleBindings(PROJECT_ID, Collections.emptyList());
        when(kubernetesService.getRoleBindings(PROJECT_ID)).thenReturn(old);

        projectService.createAccessTable(PROJECT_ID, accessTable, "u1");

        verify(kubernetesService).createRoleBindings(PROJECT_ID, roleBindingsToApply);
        verify(kubernetesService).deleteRoleBindings(PROJECT_ID, Collections.emptyList());
    }

    @Test
    void testGetAccessTable() {
        String roleRefName = "roleRefName";

        List<RoleBinding> roleBindings = List.of(new RoleBindingBuilder()
                                                     .withNewRoleRef()
                                                     .withName(roleRefName)
                                                     .endRoleRef()
                                                     .withNewMetadata()
                                                     .addToAnnotations(AccessTableDto.USERNAME, "userName")
                                                     .endMetadata()
                                                     .build());

        when(kubernetesService.getRoleBindings(PROJECT_ID)).thenReturn(roleBindings);
        when(kubernetesService.isAccessible(PROJECT_ID,
                                            "rolebindings",
                                            "rbac.authorization.k8s.io",
                                            Constants.UPDATE_ACTION)).thenReturn(true);

        AccessTableDto result = projectService.getAccessTable(PROJECT_ID);

        assertEquals(AccessTableDto.builder().grants(Map.of("userName", roleRefName)).editable(true).build(),
                     result, "AccessTable must be equal to expected");
        verify(kubernetesService).isAccessible(
            PROJECT_ID,
            "rolebindings",
            "rbac.authorization.k8s.io",
            Constants.UPDATE_ACTION);
    }
}
