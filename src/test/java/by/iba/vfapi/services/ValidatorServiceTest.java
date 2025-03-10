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
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.ContainerStageConfig;
import by.iba.vfapi.services.utils.GraphUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidatorServiceTest {

    private static final String INPUT_GRAPH = "{\n" +
            "  \"graph\": [\n" +
            "    {\n" +
            "       \"id\": \"-jRjFu5yR\",\n" +
            "       \"vertex\": true,\n" +
            "      \"value\": {\n" +
            "        \"label\": \"Read\",\n" +
            "        \"text\": \"stage\",\n" +
            "        \"desc\": \"description\",\n" +
            "        \"type\": \"read\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "       \"id\": \"cyVyU8Xfw\",\n" +
            "       \"vertex\": true,\n" +
            "      \"value\": {\n" +
            "        \"label\": \"Write\",\n" +
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
            "}";
    private static final String PIPELINE_GRAPH_VALIDATION_SUCCESSFUL = "Pipeline's Graph Validation should be successful.";

    @Mock
    private ArgoKubernetesService argoKubernetesService;
    @Mock
    private PipelineService pipelineService;
    @Mock
    private JobService jobService;
    private ValidatorService validatorService;

    @BeforeEach
    void setUp() {
        this.validatorService = new ValidatorService(jobService, argoKubernetesService, pipelineService);
    }

    @Test
    void testParseGraph() throws JsonProcessingException {
        List<GraphDto.NodeDto> expectedNodes = List.of(new GraphDto.NodeDto("-jRjFu5yR",
                        Map.of("label",
                                "Read",
                                "text",
                                "stage",
                                "desc",
                                "description",
                                "type",
                                "read")),
                new GraphDto.NodeDto("cyVyU8Xfw",
                        Map.of("label",
                                "Write",
                                "text",
                                "stage",
                                "desc",
                                "description",
                                "type",
                                "write")));
        List<GraphDto.EdgeDto> expectedEdges = List.of(new GraphDto.EdgeDto(Map.of(), "-jRjFu5yR", "cyVyU8Xfw"));

        GraphDto graphDto = GraphUtils.parseGraph(new ObjectMapper().readTree(INPUT_GRAPH));
        assertEquals(expectedNodes, graphDto.getNodes(), "Nodes must be equal to expected");
        assertEquals(expectedEdges, graphDto.getEdges(), "Edges must be equal to expected");
    }

    @Test
    void testCompletePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("2", Map.of(Constants.NODE_NAME,
                        "jobName2",
                        Constants.NODE_JOB_ID,
                        "testJob2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("3", Map.of(Constants.NODE_NAME,
                        "pipName2",
                        Constants.NODE_PIPELINE_ID,
                        "testPip2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_PIPELINE)),
                new GraphDto.NodeDto("4", Map.of(Constants.NODE_NAME,
                        "operationContainerNode",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_CONTAINER,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        PipelineService.LIMITS_MEMORY,
                        "100G",
                        PipelineService.LIMITS_CPU,
                        "1P",
                        PipelineService.REQUESTS_MEMORY,
                        "10G",
                        PipelineService.REQUESTS_CPU,
                        "100T",
                        Constants.NODE_IMAGE_PULL_SECRET_TYPE,
                        ContainerStageConfig.ImagePullSecretType.PROVIDED.name(),
                        Constants.NODE_IMAGE_PULL_SECRET_NAME,
                        Constants.NODE_IMAGE_PULL_SECRET_NAME))),
                List.of(new GraphDto.EdgeDto(Map.of(Constants.EDGE_SUCCESS_PATH,
                        Constants.EDGE_SUCCESS_PATH_POSITIVE,
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_EDGE),
                        "1",
                        "2")));
        when(jobService.checkIfJobExists(eq("testProject"), anyString())).thenReturn(true);
        when(pipelineService.checkIfPipelineExists(eq("testProject"), anyString())).thenReturn(true);
        assertDoesNotThrow(() -> validatorService.validateGraphPipeline(pipelineGraph,
                "testProject",
                "id"),
                PIPELINE_GRAPH_VALIDATION_SUCCESSFUL);
    }

    @Test
    void testCompletePipelineGraphNewPullSecret() {
        GraphDto pipelineGraph = new GraphDto(List.of(
                new GraphDto.NodeDto("1", Map.ofEntries(
                        Map.entry(Constants.NODE_NAME,
                        "operationContainerNode"),
                        Map.entry(Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_CONTAINER),
                        Map.entry(Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_LINK),
                        Map.entry(Constants.NODE_IMAGE_PULL_POLICY,
                        Constants.NODE_IMAGE_PULL_POLICY),
                        Map.entry(PipelineService.LIMITS_MEMORY,
                        "100G"),
                        Map.entry(PipelineService.LIMITS_CPU,
                        "1P"),
                        Map.entry(PipelineService.REQUESTS_MEMORY,
                        "10G"),
                        Map.entry(PipelineService.REQUESTS_CPU,
                        "100T"),
                        Map.entry(Constants.NODE_IMAGE_PULL_SECRET_TYPE,
                        ContainerStageConfig.ImagePullSecretType.NEW.name()),
                        Map.entry(Constants.NODE_REGISTRY_LINK,
                        Constants.NODE_REGISTRY_LINK),
                        Map.entry(Constants.NODE_USERNAME,
                        Constants.NODE_USERNAME),
                        Map.entry(Constants.NODE_PASSWORD,
                        Constants.NODE_PASSWORD)))),
                List.of());
        assertDoesNotThrow(() -> validatorService.validateGraphPipeline(pipelineGraph,
                        "testProject",
                        "id"),
                PIPELINE_GRAPH_VALIDATION_SUCCESSFUL);
    }

    @Test
    void testFailedPipelineGraphLackOfResourcesParams() {
        GraphDto pipelineGraph = new GraphDto(List.of(
                new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "operationContainerNode",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_CONTAINER,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        PipelineService.LIMITS_MEMORY,
                        "hello",
                        PipelineService.LIMITS_CPU,
                        "1",
                        PipelineService.REQUESTS_MEMORY,
                        "10",
                        PipelineService.REQUESTS_CPU,
                        "100",
                        Constants.NODE_IMAGE_PULL_SECRET_TYPE,
                        ContainerStageConfig.ImagePullSecretType.PROVIDED.name(),
                        Constants.NODE_IMAGE_PULL_SECRET_NAME,
                        Constants.NODE_IMAGE_PULL_SECRET_NAME))),
                List.of());
        assertThrows(BadRequestException.class, () -> validatorService.validateGraphPipeline(pipelineGraph,
                        "testProject",
                        "id"),
                "Validation should throw BadRequestException, due to the incorrect format of resources' params.");
    }

    @Test
    void testFailedPipelineGraphIncorrectResourcesParams() {
        GraphDto pipelineGraph = new GraphDto(List.of(
                new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "operationContainerNode",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_CONTAINER,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        PipelineService.LIMITS_CPU,
                        "1",
                        PipelineService.REQUESTS_MEMORY,
                        "10",
                        Constants.NODE_IMAGE_PULL_SECRET_TYPE,
                        ContainerStageConfig.ImagePullSecretType.PROVIDED.name(),
                        Constants.NODE_IMAGE_PULL_SECRET_NAME,
                        Constants.NODE_IMAGE_PULL_SECRET_NAME))),
                List.of());
        assertThrows(BadRequestException.class, () -> validatorService.validateGraphPipeline(pipelineGraph,
                        "testProject",
                        "id"),
                "Validation should throw BadRequestException, due to the lack of resources' params.");
    }

    @Test
    void testFailedPipelineGraphMalformedImage() {
        GraphDto pipelineGraph = new GraphDto(List.of(
                new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "operationContainerNode",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_CONTAINER,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_LINK,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        Constants.NODE_IMAGE_PULL_POLICY,
                        PipelineService.LIMITS_MEMORY,
                        "100G",
                        PipelineService.LIMITS_CPU,
                        "1P",
                        PipelineService.REQUESTS_MEMORY,
                        "10G",
                        PipelineService.REQUESTS_CPU,
                        "100T",
                        Constants.NODE_IMAGE_PULL_SECRET_TYPE,
                        "UNKNOWN",
                        Constants.NODE_IMAGE_PULL_SECRET_NAME,
                        Constants.NODE_IMAGE_PULL_SECRET_NAME))),
                List.of());
        assertThrows(BadRequestException.class, () -> validatorService.validateGraphPipeline(pipelineGraph,
                        "testProject",
                        "id"),
                "Validation should throw BadRequestException, due to the malformed image.");
    }

    @Test
    void testEdgelessPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("2", Map.of(Constants.NODE_NAME,
                        "jobName2",
                        Constants.NODE_JOB_ID,
                        "testJob2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of());
        when(jobService.checkIfJobExists(eq("testProject"), anyString())).thenReturn(true);
        assertDoesNotThrow(() -> validatorService.validateGraphPipeline(pipelineGraph,
                "testProject",
                "id"),
                PIPELINE_GRAPH_VALIDATION_SUCCESSFUL);
    }

    @Test
    void testNoJobOperationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1",
                Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1"))), List.of());
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                PIPELINE_GRAPH_VALIDATION_SUCCESSFUL);
    }

    @Test
    void testNoJobNamePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1",
                Map.of(Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of());
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Pipeline's Graph Validation should throw an exception.");
    }

    @Test
    void testNoJobIdPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1",
                Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of());
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Pipeline's Graph Validation should throw an exception.");
    }

    @Test
    void testNonExistingJobIdPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                "jobName",
                Constants.NODE_JOB_ID,
                "testJob1",
                Constants.NODE_OPERATION,
                Constants.NODE_OPERATION_JOB))),
                List.of());
        when(jobService.checkIfJobExists("testProject", "testJob1")).thenReturn(false);
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Pipeline's Graph Validation should throw an exception.");
    }

    @Test
    void testMalformedJobIdPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                "jobName",
                Constants.NODE_JOB_ID,
                "testJob1",
                Constants.NODE_OPERATION,
                Constants.NODE_OPERATION_JOB))),
                List.of());
        when(jobService.checkIfJobExists("testProject", "testJob1")).thenReturn(false);
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since nested job is malformed.");
    }

    @Test
    void testBadJobOperationTypePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                "jobName",
                Constants.NODE_JOB_ID,
                "testJob1",
                Constants.NODE_OPERATION,
                "abc"))), List.of());
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is a bad node operation type.");
    }

    @Test
    void testBadEdgeOperationTypePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("2", Map.of(Constants.NODE_NAME,
                        "jobName2",
                        Constants.NODE_JOB_ID,
                        "testJob2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of(new GraphDto.EdgeDto(Map.of(Constants.EDGE_SUCCESS_PATH,
                        Constants.EDGE_SUCCESS_PATH_POSITIVE,
                        Constants.NODE_OPERATION,
                        "test"), "1", "2")));
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is a bad edge operation type.");
    }

    @Test
    void testMissingSuccessPathPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("2", Map.of(Constants.NODE_NAME,
                        "jobName2",
                        Constants.NODE_JOB_ID,
                        "testJob2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of(new GraphDto.EdgeDto(Map.of(Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_EDGE),
                        "1",
                        "2")));
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is a missing success path.");
    }

    @Test
    void testBadSuccessPathPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("2", Map.of(Constants.NODE_NAME,
                        "jobName2",
                        Constants.NODE_JOB_ID,
                        "testJob2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of(new GraphDto.EdgeDto(
                        Map.of(Constants.EDGE_SUCCESS_PATH,
                                "p_@_t_h",
                                Constants.NODE_OPERATION,
                                Constants.NODE_OPERATION_EDGE),
                        "1",
                        "2")));
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is a bad success path.");
    }

    @Test
    void testBadSourcePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("2", Map.of(Constants.NODE_NAME,
                        "jobName2",
                        Constants.NODE_JOB_ID,
                        "testJob2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of(new GraphDto.EdgeDto(Map.of(Constants.EDGE_SUCCESS_PATH,
                        Constants.EDGE_SUCCESS_PATH_POSITIVE,
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_EDGE),
                        "5",
                        "2")));
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is a bad source.");
    }

    @Test
    void testBadTargetPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_NAME,
                        "jobName",
                        Constants.NODE_JOB_ID,
                        "testJob1",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB)),
                new GraphDto.NodeDto("2", Map.of(Constants.NODE_NAME,
                        "jobName2",
                        Constants.NODE_JOB_ID,
                        "testJob2",
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_JOB))),
                List.of(new GraphDto.EdgeDto(Map.of(Constants.EDGE_SUCCESS_PATH,
                        Constants.EDGE_SUCCESS_PATH_POSITIVE,
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_EDGE),
                        "1",
                        "20")));
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is a bad target.");
    }

    @Test
    void testNodelessPipelineGraph() {
        GraphDto pipelineGraph =
                new GraphDto(List.of(), List.of(new GraphDto.EdgeDto(Map.of(Constants.EDGE_SUCCESS_PATH,
                        Constants.EDGE_SUCCESS_PATH_POSITIVE,
                        Constants.NODE_OPERATION,
                        Constants.NODE_OPERATION_EDGE),
                        "1",
                        "2")));
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there are no nodes.");
    }

    @Test
    void testNotificationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_OPERATION,
                Constants.NODE_OPERATION_NOTIFICATION,
                Constants.NODE_NOTIFICATION_RECIPIENTS,
                "test",
                Constants.NODE_NOTIFICATION_MESSAGE,
                "test msg",
                Constants.NODE_NAME,
                "test_notif"))), List.of());
        assertDoesNotThrow(() -> validatorService.validateGraphPipeline(pipelineGraph,
                "testProject",
                "id"),
                PIPELINE_GRAPH_VALIDATION_SUCCESSFUL);
    }

    @Test
    void testNoNameNotificationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_OPERATION,
                Constants.NODE_OPERATION_NOTIFICATION,
                Constants.NODE_NOTIFICATION_RECIPIENTS,
                "test",
                Constants.NODE_NOTIFICATION_MESSAGE,
                "test msg"))), List.of());
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is no name for the notification node.");
    }

    @Test
    void testNoRecipientNotificationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_OPERATION,
                Constants.NODE_OPERATION_NOTIFICATION,
                Constants.NODE_NOTIFICATION_MESSAGE,
                "test msg",
                Constants.NODE_NAME,
                "test_notif"))), List.of());
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there are no recipients for the notification node.");
    }

    @Test
    void testNoMessageNotificationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_OPERATION,
                Constants.NODE_OPERATION_NOTIFICATION,
                Constants.NODE_NOTIFICATION_RECIPIENTS,
                "test",
                Constants.NODE_NAME,
                "test_notif"))), List.of());
        assertThrows(BadRequestException.class,
                () -> validatorService.validateGraphPipeline(pipelineGraph, "testProject", "id"),
                "Validation should throw BadRequestException, since there is no message for the notification node.");
    }
}