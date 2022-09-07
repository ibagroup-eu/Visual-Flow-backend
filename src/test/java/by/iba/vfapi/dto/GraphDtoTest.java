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

package by.iba.vfapi.dto;

import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.services.ArgoKubernetesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphDtoTest {

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

    @Mock
    private ArgoKubernetesService argoKubernetesService;

    @Test
    void parseGraph() throws JsonProcessingException {
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

        GraphDto graphDto = GraphDto.parseGraph(new ObjectMapper().readTree(INPUT_GRAPH));
        assertEquals(expectedNodes, graphDto.getNodes(), "Nodes must be equal to expected");
        assertEquals(expectedEdges, graphDto.getEdges(), "Edges must be equal to expected");
    }

    @Test
    void testCompletePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB)),
                                                      new GraphDto.NodeDto("2", Map.of(Constants.NODE_JOB_NAME,
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
                                                                           "2")));
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());
        assertDoesNotThrow(() -> GraphDto.validateGraphPipeline(pipelineGraph,
                                                                "testProject",
                                                                argoKubernetesService));
    }

    @Test
    void testEdgelessPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB)),
                                                      new GraphDto.NodeDto("2", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName2",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob2",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB))),
                                              List.of());
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());
        assertDoesNotThrow(() -> GraphDto.validateGraphPipeline(pipelineGraph,
                                                                "testProject",
                                                                argoKubernetesService));
    }

    @Test
    void testNoJobOperationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1",
                                                                           Map.of(Constants.NODE_JOB_NAME,
                                                                                  "jobName",
                                                                                  Constants.NODE_JOB_ID,
                                                                                  "testJob1"))), List.of());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
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
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testNoJobIdPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1",
                                                                           Map.of(Constants.NODE_JOB_NAME,
                                                                                  "jobName",
                                                                                  Constants.NODE_OPERATION,
                                                                                  Constants.NODE_OPERATION_JOB))),
                                              List.of());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testNonExistingJobIdPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB))),
                                              List.of());
        when(argoKubernetesService.getConfigMap(anyString(), anyString()))
            .thenThrow(new ResourceNotFoundException(""));
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testMalformedJobIdPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB))),
                                              List.of());
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(null);
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testBadJobOperationTypePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       "abc"))), List.of());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testBadEdgeOperationTypePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB)),
                                                      new GraphDto.NodeDto("2", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName2",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob2",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB))),
                                              List.of(new GraphDto.EdgeDto(Map.of(Constants.EDGE_SUCCESS_PATH,
                                                                                  Constants.EDGE_SUCCESS_PATH_POSITIVE,
                                                                                  Constants.NODE_OPERATION,
                                                                                  "test"), "1", "2")));
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testMissingSuccessPathPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB)),
                                                      new GraphDto.NodeDto("2", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName2",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob2",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB))),
                                              List.of(new GraphDto.EdgeDto(Map.of(Constants.NODE_OPERATION,
                                                                                  Constants.NODE_OPERATION_EDGE),
                                                                           "1",
                                                                           "2")));
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testBadSuccessPathPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB)),
                                                      new GraphDto.NodeDto("2", Map.of(Constants.NODE_JOB_NAME,
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
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testBadSourcePipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB)),
                                                      new GraphDto.NodeDto("2", Map.of(Constants.NODE_JOB_NAME,
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
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testBadTargetPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_JOB_NAME,
                                                                                       "jobName",
                                                                                       Constants.NODE_JOB_ID,
                                                                                       "testJob1",
                                                                                       Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_JOB)),
                                                      new GraphDto.NodeDto("2", Map.of(Constants.NODE_JOB_NAME,
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
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
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
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testNotificationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_NOTIFICATION,
                                                                                       Constants.NODE_NOTIFICATION_RECIPIENTS,
                                                                                       "test",
                                                                                       Constants.NODE_NOTIFICATION_MESSAGE,
                                                                                       "test msg",
                                                                                       Constants.NODE_NOTIFICATION_NAME,
                                                                                       "test_notif"))), List.of());
        assertDoesNotThrow(() -> GraphDto.validateGraphPipeline(pipelineGraph,
                                                                "testProject",
                                                                argoKubernetesService));
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
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testNoRecipientNotificationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_NOTIFICATION,
                                                                                       Constants.NODE_NOTIFICATION_MESSAGE,
                                                                                       "test msg",
                                                                                       Constants.NODE_NOTIFICATION_NAME,
                                                                                       "test_notif"))), List.of());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }

    @Test
    void testNoMessageNotificationPipelineGraph() {
        GraphDto pipelineGraph = new GraphDto(List.of(new GraphDto.NodeDto("1", Map.of(Constants.NODE_OPERATION,
                                                                                       Constants.NODE_OPERATION_NOTIFICATION,
                                                                                       Constants.NODE_NOTIFICATION_RECIPIENTS,
                                                                                       "test",
                                                                                       Constants.NODE_NOTIFICATION_NAME,
                                                                                       "test_notif"))), List.of());
        assertThrows(BadRequestException.class,
                     () -> GraphDto.validateGraphPipeline(pipelineGraph, "testProject", argoKubernetesService));
    }
}