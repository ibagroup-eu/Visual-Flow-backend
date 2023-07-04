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

import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.history.HistoryResponseDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobRequestDto;
import by.iba.vfapi.dto.jobs.JobResponseDto;
import by.iba.vfapi.dto.jobs.PipelineJobOverviewDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConflictException;
import by.iba.vfapi.model.JobParams;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.model.history.JobHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.DagTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import by.iba.vfapi.model.argo.DagTask;
import by.iba.vfapi.model.argo.Arguments;
import by.iba.vfapi.model.argo.Parameter;
import by.iba.vfapi.model.argo.Template;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {
    private static JsonNode GRAPH;

    static {
        try {
            GRAPH = new ObjectMapper().readTree("{\n" +
                                                    "  \"graph\": [\n" +
                                                    "    {\n" +
                                                    "       \"id\": \"-jRjFu5yR\",\n" +
                                                    "       \"vertex\": true,\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"operation\": \"READ\",\n" +
                                                    "        \"text\": \"stage\",\n" +
                                                    "        \"desc\": \"description\",\n" +
                                                    "        \"type\": \"read\"\n" +
                                                    "      }\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "       \"id\": \"cyVyU8Xfw\",\n" +
                                                    "       \"vertex\": true,\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"operation\": \"WRITE\",\n" +
                                                    "        \"text\": \"stage\",\n" +
                                                    "        \"desc\": \"description\",\n" +
                                                    "        \"type\": \"write\"\n" +
                                                    "      }\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"value\": {},\n" +
                                                    "      \"id\": \"4\",\n" +
                                                    "      \"edge\": true,\n" +
                                                    "      \"parent\": \"1\",\n" +
                                                    "      \"source\": \"-jRjFu5yR\",\n" +
                                                    "      \"target\": \"cyVyU8Xfw\",\n" +
                                                    "      \"successPath\": true,\n" +
                                                    "      \"mxObjectId\": \"mxCell#8\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
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
    private JobService jobService;

    @BeforeEach
    void setUp() {
        this.jobService = new JobService("image", "master", "spark", "pullSecret", "mountPath",
                "/job-config", kubernetesService, dependencyHandlerService, argoKubernetesService, authenticationService, podService, projectService, historyRepository);
    }

    @Test
    void testCreate() {
        ArgumentCaptor<ConfigMap> captor = ArgumentCaptor.forClass(ConfigMap.class);
        doNothing().when(kubernetesService).createOrReplaceConfigMap(eq("projectId"), captor.capture());
        when(kubernetesService.getConfigMap(eq("projectId"), anyString()))
            .thenReturn(new ConfigMap())
            .thenThrow(new ResourceNotFoundException(""));

        jobService.create(
            "projectId",
            JobRequestDto
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

        verify(kubernetesService, times(2)).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
        verify(kubernetesService, times(3)).getConfigMap(anyString(), anyString());

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

        JobRequestDto build =
            JobRequestDto.builder().params(new JobParams().executorCores("1")).name("name").definition(GRAPH).build();
        assertThrows(BadRequestException.class,
                     () -> jobService.create("projectId", build),
                     "Expected exception must be thrown");

        verify(kubernetesService, never()).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
    }

    @Test
    void testUpdate() {
        doNothing().when(kubernetesService).createOrReplaceConfigMap(eq("projectId"), any(ConfigMap.class));
        doNothing().when(kubernetesService).deletePod("projectId","id");
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
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String("{\"graph\":[]}".getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();

        when(kubernetesService.getConfigMap("projectId", "id")).thenReturn(configMap);
        when(projectService.getParams("projectId")).thenReturn(ParamsDto.builder().params(new LinkedList<>()).build());
        jobService.update("id",
                          "projectId",
                          JobRequestDto
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

        verify(kubernetesService, times(2)).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
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
        when(kubernetesService.getPodStatus("projectId", "id")).thenReturn(new PodStatusBuilder()
                                                                               .withPhase("Pending")
                                                                               .withStartTime(
                                                                                   "2020-10-27T10:14:46Z")
                                                                               .build());
        JobResponseDto expected = JobResponseDto
            .builder()
            .name("name")
            .definition(new ObjectMapper().readTree(GRAPH.toString().getBytes()))
            .status("Pending")
            .lastModified("lastModified")
            .startedAt("2020-10-27 10:14:46 +0000")
            .params(new JobParams().executorMemory("1").driverMemory("1").tags(List.of( "value1", "value2")))
            .build();

        assertEquals(expected, jobService.get("projectId", "id"), "Job must be equals to expected");
    }

    @Test
    void testGetHistory() {
        Map<String, JobHistory> histories = new HashMap<>();
        histories.put("1661334380617",
                new JobHistory("3b6d29b1-f717-4532-8fb6-68b339932253","job", "2022-08-24T09:45:09Z",
                        "2022-08-24T09:46:19Z","jane-doe" ,"Succeeded"));
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

        when(authenticationService.getUserInfo()).thenReturn(ui);
        when(kubernetesService.getConfigMap(projectId, id)).thenReturn(cm);
        doNothing().when(kubernetesService).createPod(eq(projectId), any(Pod.class));
        doNothing().when(kubernetesService).deletePod("projectId", "jobId");

        jobService.run(projectId, id);

        verify(kubernetesService).getConfigMap(projectId, id);
        verify(kubernetesService).deletePod(projectId, id);
        verify(kubernetesService).createPod(eq(projectId), any(Pod.class));
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

    private List<WorkflowTemplate> getMockedWorkflowTemplates() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("id1")
                .addToLabels(Constants.NAME, "name1")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());
        DagTemplate dagTemplate = new DagTemplate();
        dagTemplate.setTasks(List.of(
                new DagTask()
                        .arguments(new Arguments().addParametersItem(new Parameter()
                                .name("configMap")
                                .value("jobId")))
                        .name("pipeline")
                        .template("sparkTemplate")));
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                .name("dagTemplate")
                .dag(dagTemplate))));

        return List.of(workflowTemplate);
    }
}
