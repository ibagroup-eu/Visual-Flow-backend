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
import by.iba.vfapi.model.argo.DagTemplate;
import by.iba.vfapi.model.argo.PipelineParams;
import by.iba.vfapi.model.argo.Template;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.apache.commons.codec.binary.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyHandlerServiceTest {
    private static JsonNode GRAPH;
    private static JsonNode EMPTY_GRAPH;

    static {
        try {
            GRAPH = new ObjectMapper().readTree(
                    "{\n" +
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
                            "        \"pipelineId\": \"pl1\",\n" +
                            "        \"name\": \"testPipeline\",\n" +
                            "        \"operation\": \"PIPELINE\"\n" +
                            "      },\n" +
                            "      \"id\": \"Mdy6eqsd\",\n" +
                            "      \"vertex\": true\n" +
                            "    },\n" +
                            "    {\n" +
                            "      \"value\": {\n" +
                            "        \"successPath\": true,\n" +
                            "        \"operation\": \"EDGE\"\n" +
                            "      },\n" +
                            "      \"source\": \"jRjFu5yR\",\n" +
                            "      \"target\": \"Mdy6eqsd\"\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}");
            EMPTY_GRAPH = new ObjectMapper().readTree(
                    "{\n" +
                            "  \"graph\": [\n" +
                            "  ]\n" +
                            "}");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Mock
    private ArgoKubernetesService argoKubernetesService;
    private DependencyHandlerService dependencyHandlerService;

    @BeforeEach
    void setUp() {
        this.dependencyHandlerService = new DependencyHandlerService(
            argoKubernetesService);
    }

    @Test
    void testAddDependencies() {

        ConfigMap configMap = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.DEPENDENT_PIPELINE_IDS,
                        "pl1",
                        Constants.TAGS,
                        "value1, value2",
                        Constants.JOB_CONFIG_FIELD,
                        "{\"nodes\":[], \"edges\":[]}"))
                .withNewMetadata()
                .withName("id")
                .addToLabels(Constants.NAME, "cm1")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(EMPTY_GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("depends1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(EMPTY_GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(new HashSet<>()))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));

        when(argoKubernetesService.getConfigMap("projectId","cm1")).thenReturn(configMap);
        when(argoKubernetesService.getWorkflowTemplate("projectId", "pl1"))
                .thenReturn(workflowTemplate);

        dependencyHandlerService.updateDependenciesGraphPipeline("projectId", "pl1", GRAPH);
        verify(argoKubernetesService, times(2)).getWorkflowTemplate(anyString(),
                anyString());
    }

    @Test
    void testDeletedDependencies() {
        ConfigMap configMap = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.DEPENDENT_PIPELINE_IDS,
                        "pl1",
                        Constants.TAGS,
                        "value1, value2",
                        Constants.JOB_CONFIG_FIELD,
                        "{\"nodes\":[], \"edges\":[]}"))
                .withNewMetadata()
                .withName("id")
                .addToLabels(Constants.NAME, "cm1")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(EMPTY_GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("depends1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(new HashSet<>()))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));

        when(argoKubernetesService.getConfigMap("projectId","cm1")).thenReturn(configMap);
        when(argoKubernetesService.getWorkflowTemplate("projectId", "pl1"))
                .thenReturn(workflowTemplate);
        dependencyHandlerService.updateDependenciesGraphPipeline("projectId", "pl1", EMPTY_GRAPH);
        verify(argoKubernetesService, times(2)).getWorkflowTemplate(anyString(),
                anyString());
    }

    @Test
    void testJobHasDepends(){
        ConfigMap configMap = new ConfigMapBuilder()
                .addToData(Map.of(Constants.EXECUTOR_MEMORY,
                        "1G",
                        Constants.DRIVER_MEMORY,
                        "1G",
                        Constants.DEPENDENT_PIPELINE_IDS,
                        "pl1",
                        Constants.TAGS,
                        "value1, value2",
                        Constants.JOB_CONFIG_FIELD,
                        "{\"nodes\":[], \"edges\":[]}"))
                .withNewMetadata()
                .withName("id")
                .addToLabels(Constants.NAME, "cm1")
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.DEFINITION, Base64.encodeBase64String(EMPTY_GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .endMetadata()
                .build();

        boolean expected = true;
        boolean result = !dependencyHandlerService.jobHasDepends(configMap);
        assertEquals(expected, result, "Existence of dependencies must be equal to the expected");
    }

    @Test
    void testPipelineHasDepends(){
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("depends1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(EMPTY_GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(Set.of("pl1")))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));

        boolean expected = true;
        boolean result = !dependencyHandlerService.pipelineHasDepends(workflowTemplate);
        assertEquals(expected, result, "Existence of dependencies must be equal to the expected");
    }
}
