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

import by.iba.vfapi.config.ApplicationConfigurationProperties;
import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.history.HistoryResponseDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.PipelineJobOverviewDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConflictException;
import by.iba.vfapi.model.JobParams;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.model.history.JobHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.ExceptionUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(value = ApplicationConfigurationProperties.class)
class JobServiceTest {
    private static JsonNode GRAPH;

    static {
        try {
            GRAPH = new ObjectMapper().readTree("{\n" +
                    "\"graph\":\n" +
                    "    [\n" +
                    "        {\n" +
                    "        \"id\": \"-jRjFu5yR\",\n" +
                    "        \"value\":\n" +
                    "            {\n" +
                    "            \"desc\": \"description\",\n" +
                    "            \"operation\": \"READ\",\n" +
                    "            \"text\": \"stage\",\n" +
                    "            \"type\": \"read\",\n" +
                    "            \"connectionName\": \"connectionName\",\n" +
                    "            \"connectionId\": \"connectionId\",\n" +
                    "            \"storage\": \"db2\",\n" +
                    "            \"jdbcUrl\": \"url\",\n" +
                    "            \"user\": \"url\",\n" +
                    "            \"password\": \"password\"\n" +
                    "            },\n" +
                    "        \"vertex\": true\n" +
                    "        },\n" +
                    "        {\n" +
                    "        \"id\": \"cyVyU8Xfw\",\n" +
                    "        \"value\":\n" +
                    "            {\n" +
                    "            \"desc\": \"description\",\n" +
                    "            \"operation\": \"WRITE\",\n" +
                    "            \"text\": \"stage\",\n" +
                    "            \"type\": \"write\",\n" +
                    "            \"connectionName\": \"connectionName\",\n" +
                    "            \"connectionId\": \"connectionId\",\n" +
                    "            \"storage\": \"db2\",\n" +
                    "            \"jdbcUrl\": \"url\",\n" +
                    "            \"user\": \"user\",\n" +
                    "            \"password\": \"password\"\n" +
                    "            },\n" +
                    "        \"vertex\": true\n" +
                    "        },\n" +
                    "        {\n" +
                    "        \"edge\": true,\n" +
                    "        \"id\": \"4\",\n" +
                    "        \"mxObjectId\": \"mxCell#8\",\n" +
                    "        \"parent\": \"1\",\n" +
                    "        \"source\": \"-jRjFu5yR\",\n" +
                    "        \"successPath\": true,\n" +
                    "        \"target\": \"cyVyU8Xfw\",\n" +
                    "        \"value\":\n" +
                    "            {\n" +
                    "            }\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}");
        } catch (JsonProcessingException e) {
            ExceptionUtils.throwAsUncheckedException(e);
        }
    }

    @Mock
    private KubernetesService kubernetesService;
    @Mock
    private ArgoKubernetesService argoKubernetesService;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private PodService podService;
    @Mock
    private ProjectService projectService;
    @Mock
    private DependencyHandlerService dependencyHandlerService;
    @Mock
    private JobHistoryRepository historyRepository;
    @Mock
    private JobSessionService metadataService;
    @Mock
    private ValidatorService validatorService;
    private JobService jobService;
    @Autowired
    private ApplicationConfigurationProperties appProperties;

    @BeforeEach
    void setUp() {
        this.jobService = spy(new JobService(kubernetesService, dependencyHandlerService, argoKubernetesService,
                authenticationService, podService, projectService, historyRepository, metadataService, validatorService,
                appProperties));
    }

    @Test
    void testCreate() {
        ArgumentCaptor<ConfigMap> captor = ArgumentCaptor.forClass(ConfigMap.class);
        doNothing().when(kubernetesService).createOrReplaceConfigMap(eq("projectId"), captor.capture());
        when(kubernetesService.getConfigMap(eq("projectId"), anyString()))
                .thenReturn(new ConfigMap())
                .thenThrow(new ResourceNotFoundException(""));
        when(projectService.get("projectId")).thenReturn(ProjectResponseDto.builder().build());

        jobService.create(
                "projectId",
                JobDto
                        .builder()
                        .params(new JobParams()
                                .driverCores("1")
                                .driverMemory("1")
                                .driverRequestCores("1")
                                .executorCores("1")
                                .executorInstances("1")
                                .executorMemory("1")
                                .executorRequestCores("1")
                                .shufflePartitions("2")
                                .tags(List.of("value1", "value2")))
                        .name("name")
                        .definition(GRAPH)
                        .build());

        verify(kubernetesService, times(3)).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
        verify(kubernetesService, times(4)).getConfigMap(anyString(), anyString());

        assertEquals("1G",
                captor.getAllValues().get(0).getData().get(Constants.EXECUTOR_MEMORY),
                "Executor memory must be equal to expected");
        assertEquals("1G",
                captor.getAllValues().get(0).getData().get(Constants.DRIVER_MEMORY),
                "Driver memory must be equal to expected");
        assertNotNull(captor.getAllValues().get(1).getData().get(Constants.JOB_CONFIG_FIELD),
                "Graph should exists in other configmap");
    }

    @Test
    void testCreateNotUniqueName() {
        when(kubernetesService.getConfigMapsByLabels("projectId",
                Map.of(Constants.NAME,
                        "name"))).thenReturn(List.of(new ConfigMap(),
                new ConfigMap()));

        JobDto build =
                JobDto.builder().params(new JobParams().executorCores("1")).name("name").definition(GRAPH).build();
        assertThrows(BadRequestException.class,
                () -> jobService.create("projectId", build),
                "Expected exception must be thrown");

        verify(kubernetesService, never()).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
    }

    @Test
    void testUpdate() {
        doNothing().when(kubernetesService).createOrReplaceConfigMap(eq("projectId"), any(ConfigMap.class));
        doNothing().when(kubernetesService).deletePod("projectId", "id");
        doNothing().when(kubernetesService).deletePodsByLabels("projectId", Map.of(Constants.JOB_ID_LABEL,
                "id",
                Constants.SPARK_ROLE_LABEL,
                Constants.SPARK_ROLE_EXEC,
                Constants.PIPELINE_JOB_ID_LABEL,
                Constants.NOT_PIPELINE_FLAG));
        ConfigMap configMap = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "\\test-config\\test.json"))
                .withNewMetadata()
                .withName("jobId1")
                .addToLabels(Constants.NAME, "jobName2")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap jobDef = new ConfigMapBuilder()
                .addToData(Constants.DEFINITION, Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .withNewMetadata()
                .withName("id-def")
                .addToLabels(Constants.NAME, "name")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB_DEF)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();

        when(kubernetesService.getConfigMap("projectId", "id")).thenReturn(configMap);
        when(kubernetesService.getConfigMap("projectId", "id-def")).thenReturn(jobDef);
        when(kubernetesService.getConfigMap("projectId", "jobId1-def")).thenReturn(jobDef);
        when(projectService.getParams("projectId")).thenReturn(ParamsDto.builder().params(new LinkedList<>()).build());
        when(projectService.get("projectId")).thenReturn(ProjectResponseDto.builder().build());
        jobService.update("id",
                "projectId",
                JobDto
                        .builder()
                        .definition(GRAPH)
                        .params(new JobParams()
                                .driverCores("1")
                                .driverMemory("1G")
                                .driverRequestCores("500m")
                                .executorCores("1")
                                .executorInstances("1")
                                .executorMemory("500M")
                                .executorRequestCores("500m")
                                .shufflePartitions("2")
                                .tags(List.of("value1", "value2")))
                        .name("newName")
                        .build());

        verify(kubernetesService, times(3)).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
        verify(kubernetesService).deletePod(anyString(), anyString());
        verify(kubernetesService).deletePodsByLabels(anyString(), anyMap());
    }

    @Test
    void testDelete() {
        ConfigMap configMap = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.JOB_CONFIG_FIELD,
                        "{\"nodes\":[], \"edges\":[]}"))
                .withNewMetadata()
                .withName("jobId1")
                .addToLabels(Constants.NAME, "jobName2")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String("{\"graph\":[]}".getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();

        when(argoKubernetesService.getConfigMap("projectId", "id")).thenReturn(configMap);
        when(dependencyHandlerService.jobHasDepends(any(ConfigMap.class))).thenReturn(true);

        doNothing().when(kubernetesService).deleteConfigMap("projectId", "id-def");
        doNothing().when(kubernetesService).deleteConfigMap("projectId", "id-cfg");
        doNothing().when(kubernetesService).deleteConfigMap("projectId", "id");
        doNothing().when(kubernetesService).deletePodsByLabels("projectId", Map.of(Constants.JOB_ID_LABEL, "id"));
        when(projectService.getParams("projectId")).thenReturn(ParamsDto.builder().params(new LinkedList<>()).build());

        jobService.delete("projectId", "id");

        verify(kubernetesService).deleteConfigMap("projectId", "id");
        verify(kubernetesService).deletePodsByLabels(anyString(), anyMap());
    }

    @Test
    void testGetAll() {
        List<ConfigMap> configMaps = List.of(new ConfigMapBuilder()
                .addToData(Map.of(Constants.TAGS,
                        "value1",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "\\job-config\\test.json"))
                .withMetadata(new ObjectMetaBuilder()
                        .withName("id1")
                        .addToLabels(Constants.NAME, "name1")
                        .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                        .addToAnnotations(Constants.DEFINITION,
                                Base64.encodeBase64String(
                                        "data".getBytes()))
                        .build())
                .build());
        ConfigMap jobData = new ConfigMapBuilder()
                .addToData(Map.of(Constants.JOB_CONFIG_FIELD,
                        "{\"nodes\":[{\"id\": \"id\", \"value\": " +
                                "{}}], " +
                                "\"edges\":[]}"))
                .withNewMetadata()
                .withName("id1-cfg")
                .addToLabels(Constants.NAME, "name1")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        when(kubernetesService.getConfigMap("projectId", "id1-cfg")).thenReturn(jobData);
        when(kubernetesService.getAllConfigMaps("projectId")).thenReturn(configMaps);
        when(kubernetesService.getPodStatus("projectId", "id1")).thenReturn(new PodStatusBuilder()
                .withPhase("Pending")
                .withStartTime(
                        "2020-10-27T10:14:46Z")
                .build());

        Map<String, Quantity> hard = Map.of(Constants.LIMITS_CPU,
                Quantity.parse("20"),
                Constants.LIMITS_MEMORY,
                Quantity.parse("100Gi"),
                Constants.REQUESTS_CPU,
                Quantity.parse("20"),
                Constants.REQUESTS_MEMORY,
                Quantity.parse("100Gi"));
        ResourceQuota quota = new ResourceQuotaBuilder().withNewStatus().addToHard(hard).endStatus().build();

        List<Pod> execs = List.of(new PodBuilder().withNewMetadata().withName("1").endMetadata().build());

        when(kubernetesService.getPodsByLabels("projectId", Map.of(Constants.JOB_ID_LABEL,
                "id1",
                Constants.PIPELINE_JOB_ID_LABEL,
                Constants.NOT_PIPELINE_FLAG,
                Constants.SPARK_ROLE_LABEL,
                Constants.SPARK_ROLE_EXEC))).thenReturn(execs);

        when(kubernetesService.topPod("projectId", "1")).thenReturn(new PodMetricsBuilder()
                .addNewContainer()
                .addToUsage(Constants.CPU_FIELD,
                        Quantity.parse("5"))
                .addToUsage(Constants.MEMORY_FIELD,
                        Quantity.parse("25Gi"))
                .endContainer()
                .build());
        when(kubernetesService.topPod("projectId", "id1")).thenReturn(new PodMetricsBuilder()
                .addNewContainer()
                .addToUsage(Constants.CPU_FIELD,
                        Quantity.parse("5"))
                .addToUsage(Constants.MEMORY_FIELD,
                        Quantity.parse("25Gi"))
                .endContainer()
                .build());
        when(kubernetesService.getResourceQuota("projectId", Constants.QUOTA_NAME)).thenReturn(quota);

        when(kubernetesService.getWorkflowPods("projectId", "id1")).thenReturn(List.of(new PodBuilder()
                .withNewMetadata()
                .withName(
                        "pipelinePodName")
                .addToLabels(
                        Constants.PIPELINE_ID_LABEL,
                        "wf1")
                .endMetadata()
                .withStatus(new PodStatusBuilder()
                        .withPhase(
                                "Pending")
                        .withStartTime(
                                "2020-10-27T10:14:46Z")
                        .build())
                .build()));

        when(kubernetesService.isAccessible("projectId", "pods", "", Constants.CREATE_ACTION)).thenReturn(true);
        when(kubernetesService.isAccessible("projectId", "configmaps", "", Constants.UPDATE_ACTION)).thenReturn(
                true);

        JobOverviewListDto projectId = jobService.getAll("projectId");

        JobOverviewDto expected = JobOverviewDto
                .builder()
                .id("id1")
                .name("name1")
                .startedAt("2020-10-27 10:14:46 +0000")
                .status("Pending")
                .runnable(true)
                .usage(ResourceUsageDto.builder().cpu(0.5f).memory(0.5f).build())
                .pipelineInstances(List.of(PipelineJobOverviewDto
                        .builder()
                        .id("pipelinePodName")
                        .pipelineId("wf1")
                        .startedAt("2020-10-27 10:14:46 +0000")
                        .status("Pending")
                        .usage(ResourceUsageDto.builder().cpu(0.25f).memory(0.25f).build())
                        .build()))
                .tags(List.of("value1"))
                .dependentPipelineIds(new HashSet<>())
                .build();

        assertEquals(expected, projectId.getJobs().get(0), "Job must be equals to expected");
        assertTrue(projectId.isEditable(), "Must be true");
    }

    @Test
    void testGet() throws IOException {
        ConfigMap configMap = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.TAGS,
                        "value1,value2",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "\\job-config\\test.json"))
                .withNewMetadata()
                .withName("id")
                .addToLabels(Constants.NAME, "name")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap jobData = new ConfigMapBuilder()
                .addToData(Map.of(Constants.JOB_CONFIG_FIELD,
                        "{\"nodes\":[], \"edges\":[]}"))
                .withNewMetadata()
                .withName("id-cfg")
                .addToLabels(Constants.NAME, "name")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        when(kubernetesService.getConfigMap("projectId", "id-cfg")).thenReturn(jobData);
        when(kubernetesService.getConfigMap("projectId", "id")).thenReturn(configMap);
        when(kubernetesService.getPod("projectId", "id"))
                .thenReturn(new PodBuilder()
                        .withMetadata(new ObjectMetaBuilder()
                                .withUid("id")
                                .build())
                        .withStatus(new PodStatusBuilder()
                                .withPhase("Pending")
                                .withStartTime(
                                        "2020-10-27T10:14:46Z")
                                .build())
                        .build());
        JobDto expected = JobDto
                .builder()
                .name("name")
                .runId("id")
                .definition(new ObjectMapper().readTree(GRAPH.toString().getBytes()))
                .status("Pending")
                .lastModified("lastModified")
                .startedAt("2020-10-27 10:14:46 +0000")
                .params(new JobParams().executorMemory("1").driverMemory("1").tags(List.of("value1", "value2")))
                .build();

        assertEquals(expected, jobService.getById("projectId", "id"), "Job must be equals to expected");
    }

    @Test
    void testGetHistory() {
        Map<String, JobHistory> histories = new HashMap<>();
        histories.put("1661334380617",
                new JobHistory("3b6d29b1-f717-4532-8fb6-68b339932253", "job", "2022-08-24T09:45:09Z",
                        "2022-08-24T09:46:19Z", "jane-doe", "Succeeded"));
        when(historyRepository.findAll(anyString())).thenReturn(histories);

        List<HistoryResponseDto> historyObjects = jobService.getJobHistory("projectId", "id");
        HistoryResponseDto expected = HistoryResponseDto
                .builder()
                .id("3b6d29b1-f717-4532-8fb6-68b339932253")
                .type("job")
                .status("Succeeded")
                .startedAt("2022-08-24T09:45:09Z")
                .finishedAt("2022-08-24T09:46:19Z")
                .startedBy("jane-doe")
                .logId("1661334380617")
                .build();

        assertEquals(expected, historyObjects.get(0), "History must be equal to expected");
    }

    @Test
    void testRun() {
        String projectId = "projectId";
        String id = "jobId";
        Map<String, String> params =
                Map.of("DRIVER_CORES", "1", "DRIVER_MEMORY", "1G", "DRIVER_REQUEST_CORES", "0.1");
        ConfigMap cm =
                new ConfigMapBuilder().withNewMetadata().withName("name").endMetadata().withData(params).build();
        UserInfo ui = new UserInfo();
        ui.setUsername("test_user");

        when(authenticationService.getUserInfo()).thenReturn(Optional.of(ui));
        when(kubernetesService.getConfigMap(projectId, id)).thenReturn(cm);
        Pod pod = mock(Pod.class);
        ObjectMeta meta = mock(ObjectMeta.class);
        String expected = "uuid";
        JsonNode definition = mock(JsonNode.class);
        doReturn(JobDto.builder().definition(definition).build()).when(jobService).getById(projectId, id);
        doReturn(expected).when(meta).getUid();
        doReturn(meta).when(pod).getMetadata();
        doReturn(pod).when(kubernetesService).createPod(eq(projectId), any(PodBuilder.class));
        doNothing().when(kubernetesService).deletePod("projectId", "jobId");

        String uuid = jobService.run(projectId, id, true);

        assertEquals(expected, uuid, "result should match");
        verify(kubernetesService).getConfigMap(projectId, id);
        verify(kubernetesService).deletePod(projectId, id);
        verify(kubernetesService).createPod(eq(projectId), any(PodBuilder.class));
        verify(metadataService).createSession(uuid, definition);
    }

    @Test
    void testStop() {
        when(kubernetesService.getPodStatus("projectId", "id")).thenReturn(new PodStatusBuilder()
                .withPhase("Running")
                .build());
        doNothing().when(kubernetesService).stopPod("projectId", "id");
        jobService.stop("projectId", "id");
        verify(kubernetesService).stopPod("projectId", "id");
    }

    @Test
    void testStopException() {
        when(kubernetesService.getPodStatus("projectId", "id")).thenReturn(new PodStatusBuilder()
                .withPhase("Error")
                .build());
        assertThrows(ConflictException.class,
                () -> jobService.stop("projectId", "id"),
                "Expected exception must be thrown");

        verify(kubernetesService, never()).stopPod("projectId", "id");
    }

    @Test
    void testUpdateConnectionDetails() throws IOException {
        ConnectDto connection = ConnectDto.builder().key("connectionId")
                .value(new ObjectMapper().readTree("{\n" +
                        " \"storage\": \"db2\",\n" +
                        "        \"connectionName\": \"connectionName_new\",\n" +
                        "        \"jdbcUrl\": \"url_new\",\n" +
                        "        \"user\": \"user_new\",\n" +
                        "        \"password\": \"password_new\",\n" +
                        "        \"dependentJobIDs\": [\"id\"]\n" +
                        "}")).build();

        ConfigMap configMap = new ConfigMapBuilder()
                .addToData(Map.of(
                        Constants.SHUFFLE_PARTITIONS, "10",
                        Constants.EXECUTOR_REQUEST_CORES, "0.1",
                        Constants.EXECUTOR_INSTANCES, "2",
                        Constants.DRIVER_REQUEST_CORES, "0.1",
                        Constants.EXECUTOR_CORES, "1",
                        Constants.DRIVER_CORES, "1",
                        Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.TAGS,
                        "value1,value2",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "\\job-config\\test.json"))
                .withNewMetadata()
                .withName("id")
                .addToLabels(Constants.NAME, "name")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap jobDef = new ConfigMapBuilder()
                .addToData(Constants.DEFINITION, Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .withNewMetadata()
                .withName("id-def")
                .addToLabels(Constants.NAME, "name")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB_DEF)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap jobData = new ConfigMapBuilder()
                .addToData(Map.of(Constants.JOB_CONFIG_FIELD,
                        "{\"nodes\":[], \"edges\":[]}"))
                .withNewMetadata()
                .withName("id-cfg")
                .addToLabels(Constants.NAME, "name")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB_CONFIG)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        when(kubernetesService.getConfigMap("projectId", "id-cfg")).thenReturn(jobData);
        when(kubernetesService.getConfigMap("projectId", "id-def")).thenReturn(jobDef);

        when(kubernetesService.getConfigMap("projectId", "id")).thenReturn(configMap);
        when(kubernetesService.getPod("projectId", "id"))
                .thenReturn(new PodBuilder()
                        .withMetadata(new ObjectMetaBuilder()
                                .withUid("id")
                                .build())
                        .withStatus(new PodStatusBuilder()
                                .withPhase("Pending")
                                .withStartTime(
                                        "2020-10-27T10:14:46Z")
                                .build())
                        .build());

        when(projectService.getParams("projectId")).thenReturn(ParamsDto.builder().params(new LinkedList<>()).build());
        when(projectService.get("projectId")).thenReturn(ProjectResponseDto.builder().build());

        jobService.updateConnectionDetails(connection, "projectId");

        ArgumentCaptor<JobDto> captor = ArgumentCaptor.forClass(JobDto.class);
        verify(jobService, times(1)).update(eq("id"), eq("projectId"), captor.capture());
        String jobDefinition = captor.getValue().getDefinition().toString();

        assertFalse(GRAPH.toString().contains("connectionName_new"), "Graph should have connectionName_new");
        assertEquals(2, StringUtils.countMatches(jobDefinition, "connectionName_new"), "Graph should have connectionName_new two times");

        assertFalse(GRAPH.toString().contains("url_new"), "Graph should have url_new");
        assertEquals(2, StringUtils.countMatches(jobDefinition, "url_new"), "Graph should have url_new two times");

        assertEquals(2, StringUtils.countMatches(jobDefinition, "user_new"), "Graph should have user_new two times");
        assertEquals(2, StringUtils.countMatches(jobDefinition, "password_new"), "Graph should have password_new two times");
    }

    @SneakyThrows
    @Test
    void testCopy() {
        JsonNode graphJob = new ObjectMapper().readTree(
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
        ConfigMap configMap1 = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "test"))
                .withNewMetadata()
                .withName("jobId1")
                .addToLabels(Constants.NAME, "jobName2")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(graphJob.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap jobDefConfigMap1 = new ConfigMapBuilder()
                .addToData(Constants.DEFINITION, Base64.encodeBase64String(graphJob.toString().getBytes()))
                .withNewMetadata()
                .withName("jobId1-def")
                .addToLabels(Constants.NAME, "jobName2")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB_DEF)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap jobDataConfigMap1 = new ConfigMapBuilder()
                .addToData(Map.of(Constants.JOB_CONFIG_FIELD, "{\"nodes\":[], \"edges\":[]}"))
                .withNewMetadata()
                .withName("jobId1-cfg")
                .addToLabels(Constants.NAME, "jobName2")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB_CONFIG)
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();

        ConfigMap configMap2 = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "test"))
                .withNewMetadata()
                .withName("jobId2")
                .addToLabels(Constants.NAME, "jobName2-Copy")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(graphJob.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap configMap3 = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "test"))
                .withNewMetadata()
                .withName("jobId3")
                .addToLabels(Constants.NAME, "jobName2-Copy2")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(graphJob.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        ConfigMap configMap4 = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.JOB_CONFIG_PATH_FIELD,
                        "test"))
                .withNewMetadata()
                .withName("jobId4")
                .addToLabels(Constants.NAME, "jobName2-Copy3")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(graphJob.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();
        List<ConfigMap> configMaps = new ArrayList<>();
        configMaps.add(configMap1);
        when(argoKubernetesService.getAllConfigMaps(anyString())).thenReturn(configMaps);
        ArgumentMatcher<String> jobNameMatcher = s -> s.endsWith(Constants.JOB_DEF_SUFFIX);
        for (ConfigMap wf : List.of(configMap2, configMap3)) {
            ConfigMap original = SerializationUtils.clone(configMap1);
            when(argoKubernetesService.getConfigMap("projectId", original.getMetadata().getName()))
                    .thenReturn(original);
            doReturn(original.getMetadata().getName()).when(jobService).createFromConfigMap(eq("projectId"), any(ConfigMap.class), eq(false));
            when(argoKubernetesService.getConfigMap(eq("projectId"), argThat(jobNameMatcher)))
                    .thenReturn(jobDefConfigMap1);
            when(argoKubernetesService.getConfigMap("projectId", String.format("%s-cfg", original.getMetadata().getName())))
                    .thenReturn(jobDataConfigMap1);
            jobService.copy("projectId", original.getMetadata().getName());
            assertEquals(wf.getMetadata().getLabels().get(Constants.NAME),
                    original.getMetadata().getLabels().get(Constants.NAME),
                    "Copy suffix should be exactly the same");
            assertNotEquals(wf.getMetadata().getName(),
                    original.getMetadata().getName(),
                    "Ids should be different");
            configMaps.add(wf);
        }
        configMaps.remove(1);
        ConfigMap original = SerializationUtils.clone(configMap1);
        when(argoKubernetesService.getConfigMap("projectId", original.getMetadata().getName()))
                .thenReturn(original);
        doReturn(original.getMetadata().getName()).when(jobService).createFromConfigMap(eq("projectId"), any(ConfigMap.class), eq(false));
        when(argoKubernetesService.getConfigMap(eq("projectId"), argThat(jobNameMatcher)))
                .thenReturn(jobDefConfigMap1);
        when(argoKubernetesService.getConfigMap("projectId", String.format("%s-cfg", original.getMetadata().getName())))
                .thenReturn(jobDataConfigMap1);
        jobService.copy("projectId", original.getMetadata().getName());
        assertEquals(configMap4.getMetadata().getLabels().get(Constants.NAME),
                original.getMetadata().getLabels().get(Constants.NAME),
                "Copy suffix should be exactly the same even after deletion");
        assertNotEquals(configMap4.getMetadata().getName(),
                original.getMetadata().getName(),
                "Ids should be different even after deletion");
        configMaps.add(configMap4);
    }

    @Test
    public void updateDefinitionShouldUpdateVertexValues() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        // Set up the input data
        String jobId = "job1";
        String projectId = "proj1";
        JsonNode source = objectMapper.readTree("{\"nodes\": [{\"id\": 1, \"value\": \"newValue\"}]}");

        // Mock the getById method to return a job with a specific definition
        JobDto mockJobDto = new JobDto();
        JsonNode definition = objectMapper.readTree("{\"graph\": [{\"id\": 1, \"vertex\": true, \"value\": \"oldValue\"}]}");
        mockJobDto.setDefinition(definition);
        doReturn(mockJobDto).when(jobService).getById(projectId, jobId);
        doNothing().when(jobService).updateConfigMaps(jobId, projectId, mockJobDto);
        doNothing().when(jobService).updateParams(jobId, projectId, definition);

        // Call the method under test
        jobService.updateDefinition(jobId, projectId, source);

        // Assert that the value was updated
        JsonNode updatedDefinition = mockJobDto.getDefinition();
        assertEquals("newValue", updatedDefinition.withArray("graph").get(0).get("value").asText(),
                "Value should be updated");
    }

}
