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
import by.iba.vfapi.config.CustomNamespaceAnnotationsConfig;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ConnectionsDto;
import by.iba.vfapi.dto.projects.DemoLimitsDto;
import by.iba.vfapi.dto.projects.ParamDataDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ProjectOverviewListDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.dto.projects.ResourceQuotaRequestDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.services.utils.AccessTableUtils;
import by.iba.vfapi.services.utils.ConnectionUtils;
import by.iba.vfapi.services.utils.ParamUtils;
import by.iba.vfapi.services.utils.ProjectUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

import static by.iba.vfapi.dto.DataSource.AWS;
import static by.iba.vfapi.dto.DataSource.DATAFRAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(value = ApplicationConfigurationProperties.class)
class ProjectServiceTest {

    private static final String PROJECT_NAME = "project name";
    private static final String PROJECT_ID = "vf-project-name";
    private static final String JSON_ENCODED = "eyJuYW1lMSI6ICJ2YWx1ZTEiLCAiY29ubmVjdGlvbk5hbWUiOiAia2V5MSJ9";
    @Mock
    private KubernetesService kubernetesService;
    @Mock
    private AuthenticationService authenticationService;
    private ProjectService projectService;
    @Mock
    private JobService jobService;
    @Autowired
    private ApplicationConfigurationProperties appProperties;
    @Mock
    private LoadFilePodBuilderService filePodService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(kubernetesService,
                appProperties,
                filePodService,
                new CustomNamespaceAnnotationsConfig(),
                authenticationService,
                jobService);
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
                ProjectRequestDto.builder()
                        .name(PROJECT_NAME)
                        .description(description)
                        .limits(quotaDto)
                        .isDemo(true)
                        .demoLimits(DemoLimitsDto.builder()
                                .jobsNumAllowed(3)
                                .pipelinesNumAllowed(1)
                                .expirationDate(LocalDate.now().plusYears(1))
                                .build())
                        .build();
        ParamsDto paramsDto = ParamsDto.builder().build();
        Namespace ns = ProjectUtils.convertDtoToNamespace("id", projectDto, Map.of())
                .editMetadata().withName("vf-project-name")
                .endMetadata()
                .build();
        ResourceQuota rq = ProjectUtils.toResourceQuota(quotaDto).build();
        Secret s = ParamUtils.toSecret(paramsDto).build();
        Secret system = ProjectUtils.createSystemSecret(PROJECT_ID, appProperties);
        ConnectionsDto connectionsDto = ConnectionsDto.builder().build();
        Secret c = ConnectionUtils.toSecret(connectionsDto).build();

        doNothing().when(kubernetesService).createNamespace(ns);
        doNothing().when(kubernetesService).createOrReplaceResourceQuota(PROJECT_ID, rq);
        doNothing().when(kubernetesService).createOrReplaceSecret(PROJECT_ID, s);
        doNothing().when(kubernetesService).createOrReplaceSecret(PROJECT_ID, system);
        doNothing().when(kubernetesService).createOrReplaceSecret(PROJECT_ID, c);
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
    void testGet() throws JsonProcessingException {
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
                .addToAnnotations(Constants.DEMO_FIELD, "true")
                .addToAnnotations(Constants.DESCRIPTION_FIELD, "description")
                .addToAnnotations(Constants.VALID_TO_FIELD, LocalDate.now().plusYears(1).toString())
                .addToAnnotations(Constants.PIPELINES_LIMIT, "1")
                .addToAnnotations(Constants.JOBS_LIMIT, "1")
                .addToAnnotations(Constants.DATASOURCE_LIMIT, new ObjectMapper()
                        .writeValueAsString(Map.of("READ", List.of("DATAFRAME"), "WRITE", List.of("AWS"))))
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
        when(authenticationService.getUserInfo()).thenReturn(Optional.of(new UserInfo()));

        ProjectResponseDto result = projectService.get(PROJECT_ID);

        assertEquals(ProjectUtils
                .convertNamespaceToProjectResponse(namespace, true)
                .demo(true)
                .demoLimits(DemoLimitsDto.builder()
                        .expirationDate(LocalDate.now().plusYears(1))
                        .pipelinesNumAllowed(1)
                        .jobsNumAllowed(1)
                        .sourcesToShow(Map.of("READ", List.of(DATAFRAME), "WRITE", List.of(AWS)))
                        .build())
                .limits(ProjectUtils.getLimitsFromResourceQuota(quota).build())
                .usage(ProjectUtils.getUsageFromResourceQuota(quota).build())
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
        when(authenticationService.getUserInfo()).thenReturn(Optional.of(new UserInfo("id",
                "name",
                "username",
                "email",
                true)));

        ProjectOverviewListDto result = projectService.getAll();


        assertEquals(ProjectOverviewListDto
                .builder()
                .projects(List.of(ProjectUtils.convertNamespaceToProjectOverview(namespace, false, false)))
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
        ResourceQuota rq = ProjectUtils.toResourceQuota(quotaDto).build();
        doNothing().when(kubernetesService).createOrReplaceResourceQuota(PROJECT_ID, rq);
        doNothing().when(kubernetesService).editDescription(PROJECT_ID, "newDesc");

        projectService.update(PROJECT_ID,
                ProjectRequestDto.builder().limits(quotaDto).description("newDesc").build());

        verify(kubernetesService).createOrReplaceResourceQuota(PROJECT_ID, rq);
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
        when(kubernetesService.getSecret(PROJECT_ID, ParamUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION)).thenReturn(true);

        ParamsDto result = projectService.getParams(PROJECT_ID);

        assertEquals(ParamsDto
                .builder()
                .params(List.of(ParamDto
                        .builder()
                        .key("key")
                        .value(ParamDataDto.builder()
                                .text("value")
                                .conUsages(Collections.emptySet())
                                .jobUsages(Collections.emptySet())
                                .pipUsages(Collections.emptySet())
                                .build())
                        .secret(Boolean.FALSE)
                        .build()))
                .editable(true)
                .build(), result, "Params must be equal to expected");
        verify(kubernetesService).getSecret(PROJECT_ID, ParamUtils.SECRET_NAME);
        verify(kubernetesService).isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION);
    }

    @Test
    void testCreateParam() {
        ProjectService projectServiceSpy = spy(projectService);

        ParamDto paramDto = ParamDto
                .builder()
                .key("key2")
                .value(new ParamDataDto("ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build();
        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto.builder().key("first").build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        ParamDto result = projectServiceSpy.createParam(PROJECT_ID, "key2", paramDto);
        assertEquals(paramDto, result, "Params must be equal to expected");
    }

    @Test
    void testCreateParamIfExists() {
        ProjectService projectServiceSpy = spy(projectService);

        ParamDto paramDto = ParamDto
                .builder()
                .key("key")
                .value(new ParamDataDto("ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build();
        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto.builder().key("key").build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        assertThrows(BadRequestException.class, () -> projectServiceSpy
                .createParam(PROJECT_ID, "key", paramDto), "Expected exception must be thrown");
    }

    @Test
    void testUpdateParam() {
        ProjectService projectServiceSpy = spy(projectService);

        ParamDto paramDto = ParamDto
                .builder()
                .key("key")
                .value(new ParamDataDto("ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build();
        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto
                .builder()
                .key("key")
                .value(new ParamDataDto("ne ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        ParamDto result = projectServiceSpy.updateParam(PROJECT_ID, "key", paramDto);
        assertEquals(paramDto, result, "Params must be equal to expected");
    }

    @Test
    void testUpdateParamIfNotFound() {
        ProjectService projectServiceSpy = spy(projectService);

        ParamDto paramDto = ParamDto
                .builder()
                .key("key")
                .value(new ParamDataDto("ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build();
        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto
                .builder()
                .key("key2")
                .build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        assertThrows(ResponseStatusException.class, () -> projectServiceSpy
                .updateParam(PROJECT_ID, "key", paramDto), "Expected exception must be thrown");
    }

    @Test
    void testUpdateParamIfExists() {
        ProjectService projectServiceSpy = spy(projectService);

        ParamDto paramDto = ParamDto
                .builder()
                .key("key2")
                .value(new ParamDataDto("ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build();
        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto
                .builder()
                .key("key2")
                .build());
        allParamsList.add(ParamDto
                .builder()
                .key("key")
                .build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        assertThrows(BadRequestException.class, () -> projectServiceSpy
                .updateParam(PROJECT_ID, "key", paramDto), "Expected exception must be thrown");
    }

    @Test
    void testDeleteParam() {
        ProjectService projectServiceSpy = spy(projectService);

        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto
                .builder()
                .key("key")
                .value(new ParamDataDto("ne ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        projectServiceSpy.deleteParam(PROJECT_ID, "key");
        verify(projectServiceSpy).updateParamsWithoutProcessing(PROJECT_ID, List.of());
    }

    @Test
    void testDeleteParamIfNotFound() {
        ProjectService projectServiceSpy = spy(projectService);

        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto
                .builder()
                .key("key")
                .value(new ParamDataDto("ne ok", Collections.emptySet(), Collections.emptySet(), Collections.emptySet()))
                .build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        assertThrows(ResponseStatusException.class, () -> projectServiceSpy
                .deleteParam(PROJECT_ID, "swasdii"), "Expected exception must be thrown");
        verify(projectServiceSpy, never()).updateParamsWithoutProcessing(PROJECT_ID, List.of());
    }

    @Test
    void testDeleteParamIfHasUsages() {
        ProjectService projectServiceSpy = spy(projectService);

        List<ParamDto> allParamsList = new LinkedList<>();
        allParamsList.add(ParamDto
                .builder()
                .key("key")
                .value(new ParamDataDto("ne ok", Set.of("con1999"), Collections.emptySet(), Collections.emptySet()))
                .build());
        ParamsDto allParams = ParamsDto
                .builder()
                .params(allParamsList)
                .build();

        doReturn(allParams).when(projectServiceSpy).getParams(PROJECT_ID);
        assertThrows(BadRequestException.class, () -> projectServiceSpy
                .deleteParam(PROJECT_ID, "key"), "Expected exception must be thrown");
        verify(projectServiceSpy, never()).updateParamsWithoutProcessing(PROJECT_ID, List.of());
    }

    @Test
    void testGetConnections() throws JsonProcessingException {
        Secret secret = new SecretBuilder()
                .addToData("key", "eyJuYW1lIjogInZhbHVlIn0=")
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key", "false")
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION))
                .thenReturn(true);

        ConnectionsDto result = projectService.getConnections(PROJECT_ID);

        assertEquals(ConnectionsDto
                .builder()
                .connections(List.of(ConnectDto
                        .builder()
                        .key("key")
                        .value(new ObjectMapper().readTree("{\"name\": \"value\"}"))
                        .build()))
                .editable(true)
                .build(), result, "Connections must be equal to expected");

        verify(kubernetesService).getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME);
        verify(kubernetesService).isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION);
    }

    @Test
    void testGetConnectionsWithException() {
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenThrow(KubernetesClientException.class);

        assertThrows(ResponseStatusException.class, () -> projectService
                .getConnections(PROJECT_ID), "The project has not been found!");
        verify(kubernetesService).getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME);
        verify(kubernetesService, times(0)).isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION);
    }

    @Test
    void testGetConnection() throws JsonProcessingException {
        Secret secret = new SecretBuilder()
                .addToData("key", JSON_ENCODED)
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key", "false")
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION))
                .thenReturn(true);

        ConnectDto result = projectService.getConnection(PROJECT_ID, "key1");

        assertEquals(ConnectDto
                .builder()
                .key("key")
                .value(new ObjectMapper().readTree("{\"name1\": \"value1\", \"connectionName\": \"key1\"}"))
                .build(), result, "Connections must be equal to expected");
        verify(kubernetesService).getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME);
        verify(kubernetesService).isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION);
    }

    @Test
    void testCreateConnection() throws JsonProcessingException {
        ProjectService spyService = spy(projectService);
        Secret secret = new SecretBuilder()
                .addToData("key1", JSON_ENCODED)
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key1", "false")
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION))
                .thenReturn(true);

        ConnectDto connectDto = ConnectDto
                .builder()
                .key("key3")
                .value(new ObjectMapper().readTree("{\"myname\": \"#ok#\", \"name3\": \"value3\", " +
                        "\"connectionName\": \"connectionValue\"}"))
                .build();

        ParamsDto paramsDto = ParamsDto.builder().params(List.of(ParamDto
                .builder()
                .key("ok")
                .value(ParamDataDto.builder().conUsages(new HashSet<>()).build())
                .build())).build();
        doReturn(paramsDto).when(spyService).getParams(PROJECT_ID);

        ConnectDto result = spyService.createConnection(PROJECT_ID, connectDto);
        assertEquals(connectDto, result, "Connections must be equal to expected");
    }

    @Test
    void testCreateDuplicateConnection() throws JsonProcessingException {
        ProjectService spyService = spy(projectService);
        Secret secret = new SecretBuilder()
                .addToData("key1", JSON_ENCODED)
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key1", "false")
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION))
                .thenReturn(true);

        ConnectDto duplConnectDto = ConnectDto
                .builder()
                .key("key")
                .value(new ObjectMapper().readTree("{\"myname\": \"#ok#\", \"name3\": \"value3\", " +
                        "\"connectionName\": \"key1\"}"))
                .build();

        assertThrows(BadRequestException.class, () -> spyService
                .createConnection(PROJECT_ID, duplConnectDto), "Expected exception must be thrown");
    }

    @Test
    void testUpdateConnection() throws JsonProcessingException {
        Secret secret = new SecretBuilder()
                .addToData("key1", JSON_ENCODED)
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key1", "false")
                .endMetadata()
                .build();
        Secret paramSecret = new SecretBuilder()
                .addToData("key2", "dGVzdA===")
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key2", "true")
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION))
                .thenReturn(true);

        ConnectDto connectDto = ConnectDto
                .builder()
                .key("key1")
                .value(new ObjectMapper().readTree("{\"name1\": \"value1\", \"connectionName\": \"key1\"}"))
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ParamUtils.SECRET_NAME)).thenReturn(paramSecret);
        ConnectDto result = projectService.updateConnection(PROJECT_ID, "key1", connectDto);
        assertEquals(connectDto, result, "Connections must be equal to expected");
        assertThrows(ResponseStatusException.class, () -> projectService
                .updateConnection(PROJECT_ID, "key2", connectDto), "Expected exception must be thrown");
    }

    @Test
    void testDeleteConnection() {
        Secret secret = new SecretBuilder()
                .addToData("key1", JSON_ENCODED)
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key1", "false")
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION))
                .thenReturn(true);

        projectService.deleteConnection(PROJECT_ID, "key1");

        verify(kubernetesService).isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION);
    }

    @Test
    void testCheckConnectionDependenciesHasAdded() throws JsonProcessingException {
        JsonNode OLD_GRAPH_JOB = new ObjectMapper().readTree(
                "{\n" +
                        "  \"graph\": [\n" +
                        "    {\n" +
                        "      \"value\": {\n" +
                        "        \"jobId\": \"job1\",\n" +
                        "        \"name\": \"testJob\",\n" +
                        "        \"operation\": \"READ\"\n" +
                        "      },\n" +
                        "      \"id\": \"3\",\n" +
                        "      \"vertex\": true\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");

        JsonNode NEW_GRAPH_JOB = new ObjectMapper().readTree(
                "{\n" +
                        "  \"graph\": [\n" +
                        "    {\n" +
                        "      \"value\": {\n" +
                        "        \"jobId\": \"job1\",\n" +
                        "        \"name\": \"testJob\",\n" +
                        "        \"operation\": \"READ\",\n" +
                        "        \"storage\": \"DB2\",\n" +
                        "        \"jdbcUrl\": \"jdbcUrl\",\n" +
                        "        \"user\": \"user\",\n" +
                        "        \"password\": \"pass\",\n" +
                        "        \"connectionName\": \"connectionName\",\n" +
                        "        \"connectionId\": \"connectionId\"\n" +
                        "      },\n" +
                        "      \"id\": \"1\",\n" +
                        "      \"vertex\": true\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");

        Secret secret = new SecretBuilder()
                .addToData("connectionId",
                        Base64.encodeBase64String(("{\n " +
                                "        \"storage\": \"DB2\",\n" +
                                "        \"jdbcUrl\": \"jdbcUrl\",\n" +
                                "        \"user\": \"user\",\n" +
                                "        \"password\": \"pass\",\n" +
                                "        \"connectionName\": \"connectionName\",\n" +
                                "        \"dependentJobIDs\": \"[]\"\n" +
                                "}").getBytes()))
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key1", "false")
                .endMetadata()
                .build();
        Secret secretParam = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.getSecret(PROJECT_ID, ParamUtils.SECRET_NAME)).thenReturn(secretParam);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION)).thenReturn(true);

        projectService.checkConnectionDependencies(PROJECT_ID, "jobId", OLD_GRAPH_JOB, NEW_GRAPH_JOB);

        verify(kubernetesService, times(3)).getSecret(anyString(), anyString());
        verify(kubernetesService, times(3)).isAccessible(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testCheckConnectionDependenciesHasDeleted() throws JsonProcessingException {
        JsonNode OLD_GRAPH_JOB = new ObjectMapper().readTree(
                "{\n" +
                        "  \"graph\": [\n" +
                        "    {\n" +
                        "      \"value\": {\n" +
                        "        \"jobId\": \"job1\",\n" +
                        "        \"name\": \"testJob\",\n" +
                        "        \"operation\": \"READ\",\n" +
                        "        \"storage\": \"DB2\",\n" +
                        "        \"jdbcUrl\": \"jdbcUrl\",\n" +
                        "        \"user\": \"user\",\n" +
                        "        \"password\": \"pass\",\n" +
                        "        \"connectionName\": \"connectionName\",\n" +
                        "        \"connectionId\": \"connectionId\"\n" +
                        "      },\n" +
                        "      \"id\": \"1\",\n" +
                        "      \"vertex\": true\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");

        JsonNode NEW_GRAPH_JOB = new ObjectMapper().readTree(
                "{\n" +
                        "  \"graph\": [\n" +
                        "    {\n" +
                        "      \"value\": {\n" +
                        "        \"jobId\": \"job1\",\n" +
                        "        \"name\": \"testJob\",\n" +
                        "        \"operation\": \"READ\"\n" +
                        "      },\n" +
                        "      \"id\": \"3\",\n" +
                        "      \"vertex\": true\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");

        Secret secret = new SecretBuilder()
                .addToData("connectionId",
                        Base64.encodeBase64String(("{\n " +
                                "        \"storage\": \"DB2\",\n" +
                                "        \"jdbcUrl\": \"jdbcUrl\",\n" +
                                "        \"user\": \"user\",\n" +
                                "        \"password\": \"pass\",\n" +
                                "        \"connectionName\": \"connectionName\",\n" +
                                "        \"dependentJobIDs\": [\"job1\"]\n" +
                                "}").getBytes()))
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .addToAnnotations("key1", "false")
                .endMetadata()
                .build();
        Secret secretParam = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.getSecret(PROJECT_ID, ParamUtils.SECRET_NAME)).thenReturn(secretParam);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION)).thenReturn(true);

        projectService.checkConnectionDependencies(PROJECT_ID, "jobId", OLD_GRAPH_JOB, NEW_GRAPH_JOB);

        verify(kubernetesService, times(3)).getSecret(anyString(), anyString());
        verify(kubernetesService, times(3)).isAccessible(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testDeleteConnectionFailed() {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                .withNamespace(PROJECT_ID)
                .endMetadata()
                .build();
        when(kubernetesService.getSecret(PROJECT_ID, ConnectionUtils.SECRET_NAME)).thenReturn(secret);
        when(kubernetesService.isAccessible(PROJECT_ID, "secrets", "", Constants.UPDATE_ACTION))
                .thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> projectService
                .deleteConnection(PROJECT_ID, "key1"), "Expected exception must be thrown");
    }

    @Test
    void testCreateAccessTable() {
        Map<String, String> accessTable = Map.of("Test", "Testing", "u1", "32");
        Map<String, String> accessTableToApply = Map.of("Test", "Testing");
        AccessTableDto accessTableToApplyDto = AccessTableDto.builder().grants(accessTableToApply).build();
        List<RoleBinding> roleBindingsToApply = AccessTableUtils.toRoleBindings(accessTableToApplyDto);
        List<RoleBinding> old = List.of(new RoleBindingBuilder()
                .withNewMetadata()
                .withName("u1-32")
                .addToAnnotations(AccessTableUtils.USERNAME, "u1")
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
                .addToAnnotations(AccessTableUtils.USERNAME, "userName")
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

    @SneakyThrows
    @Test
    void testRecalculateParamsConUsages() {
        ObjectMapper mapper = new ObjectMapper();
        ParamsDto paramsDto = ParamsDto.builder().params(List.of(ParamDto
                        .builder()
                        .key("one")
                        .value(ParamDataDto.builder().conUsages(new HashSet<>()).build())
                        .build(),
                ParamDto
                        .builder()
                        .key("two")
                        .value(ParamDataDto.builder().conUsages(new HashSet<>()).build())
                        .build())).build();
        ConnectionsDto connectionsDto = ConnectionsDto.builder().connections(List.of(ConnectDto
                        .builder()
                        .key("con1")
                        .value(mapper.readTree("{\"storage\": \"#one#\",\"connectionName\": \"db2_empty\"}"))
                        .build(),
                ConnectDto
                        .builder()
                        .key("con2")
                        .value(mapper.readTree("{\"storage\": \"one\",\"connectionName\": \"db2_empty\"}"))
                        .build())).build();
        ProjectService spyService = spy(projectService);
        doReturn(paramsDto).when(spyService).getParams(PROJECT_ID);
        doReturn(connectionsDto).when(spyService).getConnections(PROJECT_ID);
        boolean actual = spyService.recalculateParamsConUsages(PROJECT_ID);
        assertTrue(actual, "Method should return true, so no errors"+
                "have been occurred!");
        verify(spyService).updateParamsWithoutProcessing(PROJECT_ID, paramsDto.getParams());
    }
}
