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
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.exporting.ExportRequestDto;
import by.iba.vfapi.dto.exporting.ExportResponseDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.dto.importing.MissingParamDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.dto.pipelines.PipelineDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ConnectionsDto;
import by.iba.vfapi.dto.projects.ParamDataDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.JobParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(value = ApplicationConfigurationProperties.class)
class TransferServiceTest {
    private static JsonNode GRAPH_WITH_PARAM;
    private static JsonNode GRAPH_WITHOUT_PARAM;
    private static JsonNode GRAPH_WITH_ONE_JOB;

    static {
        try {
            GRAPH_WITH_PARAM = new ObjectMapper().readTree("{\n" +
                    "  \"graph\": [\n" +
                    "    {\n" +
                    "      \"value\": {\n" +
                    "        \"jobId\": \"cm1\",\n" +
                    "        \"name\": \"#testJob#\",\n" +
                    "        \"operation\": \"JOB\"\n" +
                    "      },\n" +
                    "      \"id\": \"jRjFu5yR\",\n" +
                    "      \"vertex\": true\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"value\": {\n" +
                    "        \"jobId\": \"cm2\",\n" +
                    "        \"name\": \"testJob2\",\n" +
                    "        \"operation\": \"JOB\"\n" +
                    "      },\n" +
                    "      \"id\": \"cyVyU8Xfw\",\n" +
                    "      \"vertex\": true\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"value\": {\n" +
                    "        \"successPath\": true,\n" +
                    "        \"operation\": \"EDGE\"\n" +
                    "      },\n" +
                    "      \"source\": \"jRjFu5yR\",\n" +
                    "      \"target\": \"cyVyU8Xfw\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}");

            GRAPH_WITHOUT_PARAM = new ObjectMapper().readTree("{\n" +
                    "  \"graph\": [\n" +
                    "    {\n" +
                    "      \"value\": {\n" +
                    "        \"jobId\": \"cm1\",\n" +
                    "        \"name\": \"testJob\",\n" +
                    "        \"operation\": \"JOB\"\n" +
                    "      },\n" +
                    "      \"id\": \"jRjFu5yR\",\n" +
                    "      \"vertex\": true\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"value\": {\n" +
                    "        \"jobId\": \"cm2\",\n" +
                    "        \"name\": \"testJob2\",\n" +
                    "        \"operation\": \"JOB\"\n" +
                    "      },\n" +
                    "      \"id\": \"cyVyU8Xfw\",\n" +
                    "      \"vertex\": true\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"value\": {\n" +
                    "        \"successPath\": true,\n" +
                    "        \"operation\": \"EDGE\"\n" +
                    "      },\n" +
                    "      \"source\": \"jRjFu5yR\",\n" +
                    "      \"target\": \"cyVyU8Xfw\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}");
            GRAPH_WITH_ONE_JOB = new ObjectMapper().readTree("{\"graph\": [\n" +
                    "      {\n" +
                    "        \"value\": {\n" +
                    "          \"operation\": \"JOB\",\n" +
                    "          \"name\": \"read\",\n" +
                    "          \"jobId\": \"jobFromPipeline\",\n" +
                    "          \"jobName\": \"name1\"\n" +
                    "        },\n" +
                    "        \"vertex\": true\n" +
                    "      }\n" +
                    "    ]}");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Mock
    private ArgoKubernetesService argoKubernetesService;
    @Mock
    private JobService jobService;
    @Mock
    private PipelineService pipelineService;
    @Mock
    private ProjectService projectService;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(argoKubernetesService,
                jobService, pipelineService, projectService, null);
        transferService.setSelf(transferService);
    }

    @Test
    void testExporting() throws JsonProcessingException {
        PipelineDto workflowTemplate = PipelineDto.builder()
                .id("pipelineId")
                .name("name")
                .definition(GRAPH_WITH_ONE_JOB)
                .lastModified("lastModified")
                .build();
        when(pipelineService.getById("projectId", "pipelineId")).thenReturn(workflowTemplate);

        JobDto configMap1 = JobDto.builder()
                .id("jobFromPipeline")
                .name("name1")
                .definition(new ObjectMapper().readTree("{\"graph\":[]}"))
                .lastModified("lastModified")
                .params(JobParams.builder()
                        .executorMemory("1G")
                        .driverMemory("1G")
                        .build())
                .build();

        JobDto configMap2 = JobDto.builder()
                .id("jobId1")
                .name("name1")
                .definition(new ObjectMapper().readTree("{\"graph\":[]}"))
                .lastModified("lastModified")
                .params(JobParams.builder()
                        .executorMemory("1G")
                        .driverMemory("1G")
                        .build())
                .build();
        when(jobService.getById("projectId", "jobId1")).thenReturn(configMap2);
        when(jobService.getById("projectId", "jobFromPipeline")).thenReturn(configMap1);

        ExportResponseDto exporting = transferService.exporting("projectId",
                Set.of("jobId1"),
                Set.of(new ExportRequestDto.PipelineRequest(
                        "pipelineId",
                        true)));

        ExportResponseDto expected = ExportResponseDto
                .builder()
                .jobs(Set.of(configMap1, configMap2))
                .pipelines(Set.of(workflowTemplate))
                .build();

        assertEquals(expected.getJobs(), exporting.getJobs());
        assertEquals(expected.getPipelines().stream().map(PipelineDto::getDefinition).collect(Collectors.toList()),
                exporting.getPipelines().stream().map(PipelineDto::getDefinition).collect(Collectors.toList()),
                "Only one pipeline should be exported.");
    }

    @Test
    void testImporting() {
        JobDto configMap1 = JobDto.builder()
                .id("jobId1")
                .name("jobName1")
                .definition(GRAPH_WITH_PARAM)
                .lastModified("lastModified")
                .params(JobParams.builder()
                        .executorMemory("1G")
                        .driverMemory("1G")
                        .build())
                .build();

        JobDto configMap2 = JobDto.builder()
                .id("jobId2")
                .name("jobName2")
                .definition(GRAPH_WITHOUT_PARAM)
                .lastModified("lastModified")
                .params(JobParams.builder()
                        .executorMemory("1G")
                        .driverMemory("1G")
                        .build())
                .build();

        JobDto configMap3 = JobDto.builder()
                .id("jobId3")
                .name("jobName3")
                .definition(GRAPH_WITHOUT_PARAM)
                .lastModified("lastModified")
                .params(JobParams.builder()
                        .executorMemory("1G")
                        .driverMemory("1G")
                        .build())
                .build();

        PipelineDto workflowTemplate1 = PipelineDto.builder()
                .id("pipelineId1")
                .name("pipelineName1")
                .definition(GRAPH_WITHOUT_PARAM)
                .lastModified("lastModified")
                .build();

        PipelineDto workflowTemplate2 = PipelineDto.builder()
                .id("pipelineId2")
                .name("pipelineName2")
                .definition(GRAPH_WITH_PARAM)
                .lastModified("lastModified")
                .build();

        PipelineDto workflowTemplate3 = PipelineDto.builder()
                .id("pipelineId3")
                .name("pipelineName3")
                .definition(GRAPH_WITHOUT_PARAM)
                .lastModified("lastModified")
                .build();

        when(jobService.create("projectId", configMap1)).thenReturn(null);
        doThrow(BadRequestException.class).when(jobService).create("projectId", configMap2);
        when(jobService.create("projectId", configMap3)).thenReturn(null);


        when(pipelineService.create(eq("projectId"), eq("pipelineName1"), any(JsonNode.class),
                eq(workflowTemplate1.getParams()))).thenReturn(null);
        doThrow(BadRequestException.class)
                .when(pipelineService)
                .create(eq("projectId"), eq("pipelineName2"), any(JsonNode.class), eq(workflowTemplate2.getParams()));
        when(pipelineService.create(eq("projectId"), eq("pipelineName3"), any(JsonNode.class),
                eq(workflowTemplate3.getParams()))).thenReturn(null);

        List<ParamDto> paramDtos =
                Arrays.asList(ParamDto.builder()
                                .key("someKey")
                                .value(ParamDataDto.builder().text("someValue").build())
                                .secret(false)
                                .build(),
                        ParamDto.builder()
                                .key("someKey1")
                                .value(ParamDataDto.builder().text("someValue2").build())
                                .secret(false)
                                .build());
        ParamsDto paramsDto = ParamsDto.builder().editable(true).params(paramDtos).build();
        when(projectService.getParams(anyString())).thenReturn(paramsDto);

        List<ConnectDto> connections;
        try {
            connections = Arrays.asList(ConnectDto.builder().key("someKey")
                            .value(new ObjectMapper().readTree("{\"name\": \"someValue\"}")).build(),
                    ConnectDto.builder().key("someKey1")
                            .value(new ObjectMapper().readTree("{\"name\": \"someValue1\"}")).build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        ConnectionsDto connectionsDto = ConnectionsDto.builder().editable(true).connections(connections).build();
        when(projectService.getConnections(anyString())).thenReturn(connectionsDto);

        ImportResponseDto importing = transferService.importing("projectId",
                List.of(configMap1, configMap2, configMap3),
                List.of(workflowTemplate1,
                        workflowTemplate2,
                        workflowTemplate3));

        Map<String, List<MissingParamDto>> expectedParams = new HashMap<>();
        Map<String, List<MissingParamDto>> expectedConnections = new HashMap<>();
        expectedParams.put("testJob",
                Arrays.asList(MissingParamDto.builder().name("jobName1").kind("Job").nodeId("jRjFu5yR").build()));
        expectedConnections.put("testJob",
                Arrays.asList(MissingParamDto.builder().name("jobName1").kind("Job").nodeId("jRjFu5yR").build()));
        List<String> errorMessages = new ArrayList<>();
        errorMessages.add(null);
        ImportResponseDto expected = ImportResponseDto
                .builder()
                .notImportedPipelines(List.of("pipelineName2"))
                .notImportedJobs(List.of("jobName2"))
                .errorsInJobs(Map.of("jobName2", errorMessages))
                .errorsInPipelines(Map.of("pipelineName2", errorMessages))
                .missingProjectParams(expectedParams)
                .missingProjectConnections(expectedConnections)
                .build();

        assertEquals(expected, importing);
    }

    @Test
    void testCheckAccess() {
        String projectId = "projectId";
        when(argoKubernetesService.isAccessible(projectId, "configmaps", "", Constants.CREATE_ACTION))
                .thenReturn(true);
        assertTrue(transferService.checkImportAccess(projectId));
        verify(argoKubernetesService).isAccessible(anyString(), anyString(), anyString(), anyString());
    }
}
