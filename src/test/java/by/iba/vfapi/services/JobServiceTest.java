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
import by.iba.vfapi.dto.LogDto;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobRequestDto;
import by.iba.vfapi.dto.jobs.JobResponseDto;
import by.iba.vfapi.dto.jobs.PipelineJobOverviewDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConflictException;
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
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private JobService jobService;

    @BeforeEach
    void setUp() {
        this.jobService = new JobService("image", "master", "spark", "pullSecret", kubernetesService);
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
                .params(Map.of("param1",
                               "value1",
                               Constants.EXECUTOR_MEMORY,
                               "1",
                               Constants.DRIVER_MEMORY,
                               "1"))
                .name("name")
                .definition(GRAPH)
                .build());

        assertEquals("1G",
                     captor.getValue().getData().get(Constants.EXECUTOR_MEMORY),
                     "Executor memory must be equals to expected");
        assertEquals("1G",
                     captor.getValue().getData().get(Constants.DRIVER_MEMORY),
                     "Driver memory must be equals to expected");

        verify(kubernetesService).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
        verify(kubernetesService, times(2)).getConfigMap(anyString(), anyString());
    }

    @Test
    void testCreateNotUniqueName() {
        when(kubernetesService.getConfigMapsByLabels("projectId",
                                                     Map.of(Constants.NAME,
                                                            "name"))).thenReturn(List.of(new ConfigMap(),
                                                                                         new ConfigMap()));

        JobRequestDto build =
            JobRequestDto.builder().params(Map.of("param1", "value1")).name("name").definition(GRAPH).build();
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

        jobService.update("id",
                          "projectId",
                          JobRequestDto
                              .builder()
                              .definition(GRAPH)
                              .params(Map.of("param1", "value"))
                              .name("newName")
                              .build());

        verify(kubernetesService).createOrReplaceConfigMap(anyString(), any(ConfigMap.class));
        verify(kubernetesService).deletePod(anyString(), anyString());
        verify(kubernetesService).deletePodsByLabels(anyString(), anyMap());
    }

    @Test
    void testDelete() {
        doNothing().when(kubernetesService).deleteConfigMap("projectId", "id");
        doNothing().when(kubernetesService).deletePodsByLabels("projectId", Map.of(Constants.JOB_ID_LABEL, "id"));

        jobService.delete("projectId", "id");

        verify(kubernetesService).deleteConfigMap("projectId", "id");
        verify(kubernetesService).deletePodsByLabels(anyString(), anyMap());
    }

    @Test
    void testGetAll() {
        List<ConfigMap> configMaps = List.of(new ConfigMapBuilder()
                                                 .addToData(Map.of(Constants.JOB_CONFIG_FIELD,
                                                                   "{\"nodes\":[{\"id\": \"id\", \"value\": " +
                                                                       "{}}], " +
                                                                       "\"edges\":[]}"))
                                                 .withMetadata(new ObjectMetaBuilder()
                                                                   .withName("id1")
                                                                   .addToLabels(Constants.NAME, "name1")
                                                                   .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                                                                   .addToAnnotations(Constants.DEFINITION,
                                                                                     Base64.encodeBase64String(
                                                                                         "data".getBytes()))
                                                                   .build())
                                                 .build());

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
                                                                                               Constants.WORKFLOW_POD_LABEL,
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
                              Constants.JOB_CONFIG_FIELD,
                              "{\"nodes\":[], \"edges\":[]}"))
            .withNewMetadata()
            .withName("id")
            .addToLabels(Constants.NAME, "name")
            .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
            .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(GRAPH.toString().getBytes()))
            .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
            .endMetadata()
            .build();
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
            .params(Map.of(Constants.EXECUTOR_MEMORY, "1", Constants.DRIVER_MEMORY, "1"))
            .build();

        assertEquals(expected, jobService.get("projectId", "id"), "Job must be equals to expected");
    }

    @Test
    void testGetLogs() {
        String logs =
            "2020-09-29 11:02:23,180 [shutdown-hook-0] [/] INFO  org.apache.spark.SparkContext - Invoking stop()" +
                " from shutdown hook\n" +
                "AND SOMETHING ELSE\n" +
                "2020-09-29 11:02:23,197 [shutdown-hook-0] [/] INFO  o.s.jetty.server.AbstractConnector - Stopped";

        when(kubernetesService.getParsedPodLogs("projectId", "id")).thenCallRealMethod();
        when(kubernetesService.getPodLogs("projectId", "id")).thenReturn(logs);

        List<LogDto> logsObjects = jobService.getJobLogs("projectId", "id");
        LogDto expected = LogDto
            .builder()
            .message("org.apache.spark.SparkContext - Invoking stop() from shutdown hook\nAND SOMETHING ELSE")
            .level("INFO")
            .timestamp("2020-09-29 11:02:23,180")
            .build();

        assertEquals(2, logsObjects.size(), "Size must be equals to 2");
        assertEquals(expected, logsObjects.get(0), "Logs must be equal to expected");
    }

    @Test
    void testGetLogsNotFound() {
        when(kubernetesService.getParsedPodLogs("projectId", "id")).thenCallRealMethod();
        when(kubernetesService.getPodLogs("projectId", "id")).thenThrow(ResourceNotFoundException.class);

        List<LogDto> logsObjects = jobService.getJobLogs("projectId", "id");

        assertEquals(0, logsObjects.size(), "Size must be equals to expected");
    }

    @Test
    void testGetLogsFailure() {
        when(kubernetesService.getParsedPodLogs("projectId", "id")).thenCallRealMethod();
        when(kubernetesService.getPodLogs("projectId", "id")).thenThrow(new KubernetesClientException("Not found",
                                                                                                      500,
                                                                                                      null));

        assertThrows(KubernetesClientException.class,
                     () -> jobService.getJobLogs("projectId", "id"),
                     "Expected exception must be thrown");
    }

    @Test
    void testRun() {
        String projectId = "projectId";
        String id = "jobId";
        Map<String, String> params =
            Map.of("DRIVER_CORES", "1", "DRIVER_MEMORY", "1G", "DRIVER_REQUEST_CORES", "0.1");
        ConfigMap cm =
            new ConfigMapBuilder().withNewMetadata().withName("name").endMetadata().withData(params).build();

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
}
