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

import by.iba.vfapi.dao.PipelineHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.history.PipelineHistoryResponseDto;
import by.iba.vfapi.dto.history.PipelineNodesHistoryResponseDto;
import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewListDto;
import by.iba.vfapi.dto.pipelines.PipelineResponseDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.model.argo.Arguments;
import by.iba.vfapi.model.argo.CronWorkflow;
import by.iba.vfapi.model.argo.CronWorkflowSpec;
import by.iba.vfapi.model.argo.DagTask;
import by.iba.vfapi.model.argo.DagTemplate;
import by.iba.vfapi.model.argo.NodeStatus;
import by.iba.vfapi.model.argo.Parameter;
import by.iba.vfapi.model.argo.PipelineParams;
import by.iba.vfapi.model.argo.Template;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.argo.WorkflowSpec;
import by.iba.vfapi.model.argo.WorkflowStatus;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.model.history.AbstractHistory;
import by.iba.vfapi.model.history.PipelineHistory;
import by.iba.vfapi.model.history.PipelineNodeHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.argoproj.workflow.ApiException;
import io.argoproj.workflow.apis.WorkflowServiceApi;
import io.argoproj.workflow.models.WorkflowResumeRequest;
import io.argoproj.workflow.models.WorkflowRetryRequest;
import io.argoproj.workflow.models.WorkflowStopRequest;
import io.argoproj.workflow.models.WorkflowSuspendRequest;
import io.argoproj.workflow.models.WorkflowTerminateRequest;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.json.JsonParseException;

import static by.iba.vfapi.dto.Constants.NODE_TYPE_POD;
import static by.iba.vfapi.dto.Constants.PIPELINE_HISTORY;
import static by.iba.vfapi.dto.Constants.PIPELINE_NODE_HISTORY;
import static by.iba.vfapi.services.K8sUtils.FAILED_STATUS;
import static by.iba.vfapi.services.K8sUtils.RUNNING_STATUS;
import static by.iba.vfapi.services.K8sUtils.SUSPENDED_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {
    private static JsonNode GRAPH;

    static {
        try {
            GRAPH = new ObjectMapper().readTree("{\n" +
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
                                                    "        \"name\": \"testWait\",\n" +
                                                    "        \"operation\": \"WAIT\"\n" +
                                                    "      },\n" +
                                                    "      \"id\": \"Mdy6eqsd\",\n" +
                                                    "      \"vertex\": true\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"jobId\": \"cm3\",\n" +
                                                    "        \"name\": \"testJob3\",\n" +
                                                    "        \"operation\": \"JOB\"\n" +
                                                    "      },\n" +
                                                    "      \"id\": \"ydFdss83s\",\n" +
                                                    "      \"vertex\": true\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"successPath\": true,\n" +
                                                    "        \"operation\": \"EDGE\"\n" +
                                                    "      },\n" +
                                                    "      \"source\": \"jRjFu5yR\",\n" +
                                                    "      \"target\": \"cyVyU8Xfw\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"successPath\": true,\n" +
                                                    "        \"operation\": \"EDGE\"\n" +
                                                    "      },\n" +
                                                    "      \"source\": \"cyVyU8Xfw\",\n" +
                                                    "      \"target\": \"Mdy6eqsd\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"successPath\": true,\n" +
                                                    "        \"operation\": \"EDGE\"\n" +
                                                    "      },\n" +
                                                    "      \"source\": \"Mdy6eqsd\",\n" +
                                                    "      \"target\": \"ydFdss83s\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Mock
    private ArgoKubernetesService argoKubernetesService;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private ProjectService projectService;
    @Mock
    private DependencyHandlerService dependencyHandlerService;
    @Mock
    private WorkflowServiceApi apiInstance;
    private PipelineService pipelineService;
    @Mock
    private PipelineHistoryRepository<? extends AbstractHistory> pipelineHistoryRepository;

    @BeforeEach
    void setUp() {
        this.pipelineService = new PipelineService("sparkImage",
            "sparkMaster",
            "spark",
            "pullSecret",
            "slackImage",
            "pvcMountPath",
            argoKubernetesService,
            projectService,
            apiInstance,
            workflowService,
            authenticationService,
            dependencyHandlerService,
            pipelineHistoryRepository);
    }

    @Test
    void testCreate() {
        when(argoKubernetesService.getWorkflowTemplate(eq("projectId"), anyString()))
                .thenThrow(new ResourceNotFoundException(""));
        Map<String, String> res =
                Map.of("DRIVER_CORES", "1", "DRIVER_MEMORY", "1G", "DRIVER_REQUEST_CORES", "0.1");
        ConfigMap configMap =
                new ConfigMapBuilder().withNewMetadata().withName("name").endMetadata().withData(res).build();
        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(configMap);
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());

        pipelineService.create("projectId", "name", GRAPH, new PipelineParams()
                .successNotify(true)
                .failureNotify(false)
                .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                .tags(Arrays.asList("VF-Demo", "VF-Migration")));

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testCreateWithPipelineStage() throws JsonProcessingException {
        JsonNode GRAPH_PIPELINE = new ObjectMapper().readTree(
                "{\n" +
                "  \"graph\": [\n" +
                "    {\n" +
                "      \"value\": {\n" +
                "        \"pipelineId\": \"pl1\",\n" +
                "        \"name\": \"testPipeline\",\n" +
                "        \"operation\": \"PIPELINE\"\n" +
                "      },\n" +
                "      \"id\": \"3\",\n" +
                "      \"vertex\": true\n" +
                "    }\n" +
                "  ]\n" +
                "}");

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("pl1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToLabels(Constants.TYPE, "pipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String("GRAPH".getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(new HashSet<>()))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));

        when(argoKubernetesService.getWorkflowTemplate(eq("projectId"), anyString()))
                .thenThrow(new ResourceNotFoundException(""));

        doNothing()
                .when(argoKubernetesService)
                .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(argoKubernetesService.isWorkflowTemplateExist(anyString(), anyString())).thenReturn(true);
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());

        pipelineService.create("projectId", "name", GRAPH_PIPELINE, new PipelineParams()
                .successNotify(true)
                .failureNotify(false)
                .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                .tags(Arrays.asList("VF-Demo", "VF-Migration")));

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(),
                any(WorkflowTemplate.class));
    }

    @Test
    void testCreateWithContainerStage() throws JsonProcessingException {
        when(argoKubernetesService.getWorkflowTemplate(eq("projectId"), anyString()))
                .thenThrow(new ResourceNotFoundException(""));

        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());

        pipelineService.create("projectId",
                               "name",
                               new ObjectMapper().readTree("{\"graph\": [\n" +
                                                               "      {\n" +
                                                               "        \"value\": {\n" +
                                                               "          \"name\": " +
                                                               "\"example_container_stage" +
                                                               "\",\n" +
                                                               "          \"image\": \"imageLink\",\n" +
                                                               "          \"imagePullPolicy" +
                                                               "\": \"Always\",\n" +
                                                               "\"mountProjectParams\": " +
                                                               "\"true\",\n" +
                                                               "          \"limitsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"requestsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"limitsMemory\":" +
                                                               " \"1\",\n" +
                                                               "          " +
                                                               "\"requestsMemory\": \"1\"," +
                                                               "\n" +
                                                               "          " +
                                                               "\"imagePullSecretType\": " +
                                                               "\"NOT_APPLICABLE\",\n" +
                                                               "          \"operation\": " +
                                                               "\"CONTAINER\"\n" +
                                                               "        },\n" +
                                                               "        \"id\": \"2\",\n" +
                                                               "        \"vertex\": true\n" +
                                                               "      }]}"),
                new PipelineParams()
                .successNotify(true)
                .failureNotify(false)
                .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                .tags(Arrays.asList("VF-Demo", "VF-Migration")));

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }


    @Test
    void testCreateWithContainerStageWithCommand() throws JsonProcessingException {
        when(argoKubernetesService.getWorkflowTemplate(eq("projectId"), anyString()))
                .thenThrow(new ResourceNotFoundException(""));

        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());
        pipelineService.create("projectId",
                               "name",
                               new ObjectMapper().readTree("{\"graph\": [\n" +
                                                               "      {\n" +
                                                               "        \"value\": {\n" +
                                                               "          \"name\": " +
                                                               "\"example_container_stage" +
                                                               "\",\n" +
                                                               "          \"image\": \"imageLink\",\n" +
                                                               "          \"imagePullPolicy" +
                                                               "\": \"Always\",\n" +
                                                               "          \"command\": " +
                                                               "\"echo Hello World!\",\n" +
                                                               "          " +
                                                               "\"mountProjectParams\": " +
                                                               "\"true\",\n" +
                                                               "          \"limitsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"requestsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"limitsMemory\":" +
                                                               " \"1\",\n" +
                                                               "          " +
                                                               "\"requestsMemory\": \"1\"," +
                                                               "\n" +
                                                               "          " +
                                                               "\"imagePullSecretType\": " +
                                                               "\"NOT_APPLICABLE\",\n" +
                                                               "          \"operation\": " +
                                                               "\"CONTAINER\"\n" +
                                                               "        },\n" +
                                                               "        \"id\": \"2\",\n" +
                                                               "        \"vertex\": true\n" +
                                                               "      }]}"),
                new PipelineParams()
                        .successNotify(true)
                        .failureNotify(false)
                        .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                        .tags(Arrays.asList("VF-Demo", "VF-Migration")));

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testCreateWithContainerStageWithPrivateImageAndNewSecret() throws JsonProcessingException {
        when(argoKubernetesService.getWorkflowTemplate(eq("projectId"), anyString()))
                .thenThrow(new ResourceNotFoundException(""));

        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());
        when(argoKubernetesService.getSecret(anyString(),
                                             anyString())).thenThrow(new ResourceNotFoundException(""));
        pipelineService.create("projectId",
                               "name",
                               new ObjectMapper().readTree("{\"graph\": [\n" +
                                                               "      {\n" +
                                                               "        \"value\": {\n" +
                                                               "          \"name\": " +
                                                               "\"example_container_stage" +
                                                               "\",\n" +
                                                               "          \"image\": \"testRegistry/testImage\"," +
                                                               "\n" +
                                                               "          \"imagePullPolicy" +
                                                               "\": \"Always\",\n" +
                                                               "\"mountProjectParams\": " +
                                                               "\"true\",\n" +
                                                               "          \"limitsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"requestsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"limitsMemory\":" +
                                                               " \"1\",\n" +
                                                               "          " +
                                                               "\"requestsMemory\": \"1\"," +
                                                               "\n" +
                                                               "          " +
                                                               "\"imagePullSecretType\": " +
                                                               "\"NEW\",\n" +
                                                               "          \"username\": " +
                                                               "\"test_user\",\n" +
                                                               "          \"password\": " +
                                                               "\"test_pw\",\n" +
                                                               "          \"registry\": " +
                                                               "\"testRegistry\",\n" +
                                                               "          \"operation\": " +
                                                               "\"CONTAINER\"\n" +
                                                               "        },\n" +
                                                               "        \"id\": \"2\",\n" +
                                                               "        \"vertex\": true\n" +
                                                               "      }]}"),
                new PipelineParams()
                        .successNotify(true)
                        .failureNotify(false)
                        .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                        .tags(Arrays.asList("VF-Demo", "VF-Migration")));

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testCreateWithContainerStageWithPrivateImageAndProvidedSecret() throws JsonProcessingException {
        when(argoKubernetesService.getWorkflowTemplate(eq("projectId"), anyString()))
                .thenThrow(new ResourceNotFoundException(""));

        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());
        when(argoKubernetesService.isSecretExist(anyString(),anyString())).thenReturn(true);
        when(argoKubernetesService.getSecret(eq("projectId"), eq("existingSecret"))).thenReturn(new SecretBuilder()
                                                                                                    .withNewMetadata()
                                                                                                    .withNamespace(
                                                                                                        "projectId")
                                                                                                    .endMetadata()
                                                                                                    .build());
        pipelineService.create("projectId",
                               "name",
                               new ObjectMapper().readTree("{\"graph\": [\n" +
                                                               "      {\n" +
                                                               "        \"value\": {\n" +
                                                               "          \"name\": " +
                                                               "\"example_container_stage" +
                                                               "\",\n" +
                                                               "          \"image\": \"testRegistry/testImage\"," +
                                                               "\n" +
                                                               "          \"imagePullPolicy" +
                                                               "\": \"Always\",\n" +
                                                               "\"mountProjectParams\": " +
                                                               "\"true\",\n" +
                                                               "          \"limitsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"requestsCpu\": " +
                                                               "\"1\",\n" +
                                                               "          \"limitsMemory\":" +
                                                               " \"1\",\n" +
                                                               "          " +
                                                               "\"requestsMemory\": \"1\"," +
                                                               "\n" +
                                                               "          " +
                                                               "\"imagePullSecretType\": " +
                                                               "\"PROVIDED\",\n" +
                                                               "          \"imagePullSecretName\": " +
                                                               "\"existingSecret\",\n" +
                                                               "          \"operation\": " +
                                                               "\"CONTAINER\"\n" +
                                                               "        },\n" +
                                                               "        \"id\": \"2\",\n" +
                                                               "        \"vertex\": true\n" +
                                                               "      }]}"),
                new PipelineParams()
                        .successNotify(true)
                        .failureNotify(false)
                        .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                        .tags(Arrays.asList("VF-Demo", "VF-Migration")));

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testCreateNotUniqueName() {
        when(argoKubernetesService.getWorkflowTemplatesByLabels("projectId",
                                                                Map.of(Constants.NAME,
                                                                       "name"))).thenReturn(List.of(new WorkflowTemplate(),
                                                                                                    new WorkflowTemplate()));
        assertThrows(BadRequestException.class,
                     () -> pipelineService.create("projectId", "name", GRAPH, new PipelineParams()
                                     .successNotify(true)
                                     .failureNotify(false)
                                     .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                                     .tags(Arrays.asList("VF-Demo", "VF-Migration"))),
                     "Expected exception must be thrown");


        verify(argoKubernetesService, never()).createOrReplaceWorkflowTemplate(anyString(),
                                                                               any(WorkflowTemplate.class));
    }

    @Test
    void testGetById() throws IOException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        CronWorkflow cronWorkflow = new CronWorkflow();
        CronWorkflowSpec cronWorkflowSpec =new CronWorkflowSpec();
        cronWorkflowSpec.setSuspend(true);
        cronWorkflow.setSpec(cronWorkflowSpec);
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name("dagTemplate")
                                                                                  .dag(new DagTemplate().addTasksItem(
                                                                                      new DagTask())))));

        when(argoKubernetesService.getWorkflowTemplate("projectId", "id"))
                .thenReturn(workflowTemplate);
        when(argoKubernetesService.getWorkflow("projectId", "id"))
                .thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.getCronWorkflow("projectId", "id")).thenReturn(cronWorkflow);
        when(argoKubernetesService.isCronWorkflowReadyOrExist("projectId", "id")).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflows",
                                                "argoproj.io",
                                                Constants.CREATE_ACTION)).thenReturn(true);

        PipelineResponseDto expected = ((PipelineResponseDto) new PipelineResponseDto()
            .id("id")
            .name("name")
            .lastModified("lastModified")
            .status("Draft")
            .cron(true)
            .cronSuspend(true)
            .runnable(true)).definition(new ObjectMapper().readTree(GRAPH.toString().getBytes())).editable(true);

        assertEquals(expected, pipelineService.getById("projectId", "id"),
                "Pipeline must be equals to expected");

        assertEquals(expected.getDefinition().toString(),
                pipelineService.getById("projectId", "id").getDefinition().toString(),
                "Definition must be equals to expected");

        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("id")
                .addToLabels(Constants.NAME, "name")
                .addToAnnotations(Constants.DEFINITION, "test")
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());
        when(argoKubernetesService.getWorkflowTemplate("projectId", "id"))
                .thenReturn(workflowTemplate);
        when(argoKubernetesService.isCronWorkflowReadyOrExist("projectId", "id")).thenReturn(false);
        assertThrows(InternalProcessingException.class, () -> pipelineService.getById("projectId", "id"),
                "Expected exception must be thrown");
    }

    @Test
    void testGetAllInProject() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();

        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        DagTemplate dagTemplate = new DagTemplate();
        dagTemplate.setTasks(List.of(new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("1")))
                                         .name("pipeline")
                                         .template("sparkTemplate"),
                                     new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("2")))
                                         .name("pipeline-2681521834")
                                         .template("notificationTemplate")));
        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .failureNotify(false)
                        .successNotify(false)
                        .recipients(Collections.emptyList())
                        .tags(List.of("value1"))
                        .dependentPipelineIds(List.of("")))
                .templates(List.of(new Template()
                        .name("dagTemplate")
                        .dag(dagTemplate))));
        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);
        Workflow workflow = new Workflow();
        WorkflowStatus status = new WorkflowStatus();
        status.setFinishedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setStartedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setPhase("Running");
        NodeStatus nodeStatus1 = new NodeStatus();
        nodeStatus1.setDisplayName("pipeline");
        nodeStatus1.setPhase("Running");
        nodeStatus1.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus1.setTemplateName("sparkTemplate");
        nodeStatus1.setType(NODE_TYPE_POD);
        NodeStatus nodeStatus2 = new NodeStatus();
        nodeStatus2.setDisplayName("pipeline-2681521834");
        nodeStatus2.setPhase("Pending");
        nodeStatus2.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus2.setTemplateName("notificationTemplate");
        nodeStatus2.setType(NODE_TYPE_POD);
        status.setNodes(List
                            .of(nodeStatus1, nodeStatus2)
                            .stream()
                            .collect(Collectors.toMap(NodeStatus::getDisplayName, ns -> ns)));
        status.setStoredTemplates(Map.of("dagTemplate",
                                         new Template()
                                             .name(Constants.DAG_TEMPLATE_NAME)
                                             .dag(dagTemplate)
                                             .name("dagTemplate"),
                                         "notificationTemplate",
                                         new Template().name(PipelineService.NOTIFICATION_TEMPLATE_NAME),
                                         "sparkTemplate",
                                         new Template().name(PipelineService.SPARK_TEMPLATE_NAME)));
        workflow.setStatus(status);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1")).thenReturn(workflow);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflows",
                                                "argoproj.io",
                                                Constants.CREATE_ACTION)).thenReturn(true);

        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .lastModified("lastModified")
            .startedAt("2020-10-27 10:14:46 +0000")
            .finishedAt("2020-10-27 10:14:46 +0000")
            .status("Running")
            .progress(0.0f)
            .cron(false)
            .runnable(true)
            .jobsStatuses(Map.of("1", "Running", "2", "Pending"))
            .dependentPipelineIds(List.of(""))
            .tags(List.of("value1"));

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testGetAllInProjectCron() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();

        DagTemplate dagTemplate = new DagTemplate();
        dagTemplate.setTasks(List.of(new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("1")))
                                         .name("pipeline")
                                         .template("sparkTemplate"),
                                     new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("2")))
                                         .name("pipeline-2681521834")
                                         .template("notificationTemplate")));
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .failureNotify(false)
                        .successNotify(false)
                        .recipients(Collections.emptyList())
                        .tags(List.of("value1"))
                        .dependentPipelineIds(List.of("")))
                .templates(List.of(new Template()
                        .name("dagTemplate")
                        .dag(dagTemplate))));

        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);
        Workflow workflow = new Workflow();
        CronWorkflow cronWorkflow = new CronWorkflow();
        CronWorkflowSpec cronWorkflowSpec =new CronWorkflowSpec();
        cronWorkflowSpec.setSuspend(true);
        cronWorkflow.setSpec(cronWorkflowSpec);
        WorkflowStatus status = new WorkflowStatus();
        status.setFinishedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setStartedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setPhase("Running");
        NodeStatus nodeStatus1 = new NodeStatus();
        nodeStatus1.setDisplayName("pipeline");
        nodeStatus1.setPhase("Running");
        nodeStatus1.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus1.setTemplateName("sparkTemplate");
        nodeStatus1.setType(NODE_TYPE_POD);
        NodeStatus nodeStatus2 = new NodeStatus();
        nodeStatus2.setDisplayName("pipeline-2681521834");
        nodeStatus2.setPhase("Pending");
        nodeStatus2.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus2.setTemplateName("notificationTemplate");
        nodeStatus2.setType(NODE_TYPE_POD);
        status.setNodes(List
                            .of(nodeStatus1, nodeStatus2)
                            .stream()
                            .collect(Collectors.toMap(NodeStatus::getDisplayName, ns -> ns)));
        status.setStoredTemplates(Map.of("dagTemplate",
                                         new Template()
                                             .name(Constants.DAG_TEMPLATE_NAME)
                                             .dag(dagTemplate)
                                             .name("dagTemplate"),
                                         "notificationTemplate",
                                         new Template().name(PipelineService.NOTIFICATION_TEMPLATE_NAME),
                                         "sparkTemplate",
                                         new Template().name(PipelineService.SPARK_TEMPLATE_NAME)));
        workflow.setStatus(status);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1")).thenReturn(workflow);
        when(argoKubernetesService.getCronWorkflow("projectId", "id1")).thenReturn(cronWorkflow);
        when(argoKubernetesService.isCronWorkflowReadyOrExist("projectId", "id1")).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflows",
                                                "argoproj.io",
                                                Constants.CREATE_ACTION)).thenReturn(true);
        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .lastModified("lastModified")
            .startedAt("2020-10-27 10:14:46 +0000")
            .finishedAt("2020-10-27 10:14:46 +0000")
            .status("Running")
            .progress(0.0f)
            .runnable(true)
            .jobsStatuses(Map.of("1", "Running", "2", "Pending"))
            .cron(true)
            .cronSuspend(true)
            .tags(List.of("value1"))
            .dependentPipelineIds(List.of(""));

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testGetAllInProjectWithoutWorkflow() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .failureNotify(false)
                        .successNotify(false)
                        .recipients(Collections.emptyList())
                        .tags(List.of("value1"))
                        .dependentPipelineIds(List.of("")))
                .templates(List.of(new Template()
                        .name("dagTemplate")
                        .dag(new DagTemplate().addTasksItem(
                                new DagTask())))));
        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1"))
                .thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflows",
                                                "argoproj.io",
                                                Constants.CREATE_ACTION)).thenReturn(true);

        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .status("Draft")
            .lastModified("lastModified")
            .cron(false)
            .runnable(true)
            .tags(List.of("value1"))
            .dependentPipelineIds(List.of(""));

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testGetAllInProjectWithoutWorkflowCron() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        CronWorkflow cronWorkflow = new CronWorkflow();
        CronWorkflowSpec cronWorkflowSpec = new CronWorkflowSpec();
        cronWorkflowSpec.setSuspend(true);
        cronWorkflow.setSpec(cronWorkflowSpec);
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .templates(List.of(new Template()
                        .name("dagTemplate")
                        .dag(new DagTemplate().addTasksItem(
                                new DagTask()))))
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(List.of("pl1"))));
        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1"))
                .thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.getCronWorkflow("projectId", "id1")).thenReturn(cronWorkflow);
        when(argoKubernetesService.isCronWorkflowReadyOrExist("projectId", "id1")).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflows",
                                                "argoproj.io",
                                                Constants.CREATE_ACTION)).thenReturn(true);

        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .status("Draft")
            .lastModified("lastModified")
            .runnable(true)
            .cron(true)
            .cronSuspend(true)
            .tags(Collections.emptyList())
            .dependentPipelineIds(List.of("pl1"));

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testUpdate() {
        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        Map<String, String> res =
                Map.of("DRIVER_CORES", "1", "DRIVER_MEMORY", "1G", "DRIVER_REQUEST_CORES", "0.1");
        ConfigMap configMap =
                new ConfigMapBuilder().withNewMetadata().withName("name").endMetadata().withData(res).build();
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(configMap);
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("pl1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToLabels(Constants.TYPE, "pipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(null))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));

        PipelineParams params =  new PipelineParams()
                .successNotify(true)
                .failureNotify(false)
                .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                .tags(Arrays.asList("VF-Demo", "VF-Migration"))
                .dependentPipelineIds(List.of("pl2", "pl3"));

        pipelineService.update("projectId", "id", GRAPH, params, "newName");

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testUpdateWithPipelineStage() throws JsonProcessingException {
        JsonNode GRAPH_PIPELINE = new ObjectMapper().readTree(
                "{\n" +
                        "  \"graph\": [\n" +
                        "    {\n" +
                        "      \"value\": {\n" +
                        "        \"pipelineId\": \"pl1\",\n" +
                        "        \"name\": \"testPipeline\",\n" +
                        "        \"operation\": \"PIPELINE\"\n" +
                        "      },\n" +
                        "      \"id\": \"3\",\n" +
                        "      \"vertex\": true\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}");
        doNothing()
                .when(argoKubernetesService)
                .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        Map<String, String> res =
                Map.of("DRIVER_CORES", "1", "DRIVER_MEMORY", "1G", "DRIVER_REQUEST_CORES", "0.1");
        ConfigMap configMap =
                new ConfigMapBuilder().withNewMetadata().withName("name").endMetadata().withData(res).build();
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(configMap);
        when(projectService.getParams(anyString())).thenReturn(ParamsDto.fromSecret(new Secret()).build());

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("pl1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToLabels(Constants.TYPE, "pipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(GRAPH_PIPELINE.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(null))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));

        PipelineParams params = new PipelineParams()
                .successNotify(true)
                .failureNotify(false)
                .recipients(Arrays.asList("JaneDoe", "DoeJane"))
                .tags(Arrays.asList("VF-Demo", "VF-Migration"))
                .dependentPipelineIds(List.of("pl2", "pl3"));

        pipelineService.update("projectId", "id", GRAPH, params, "newName");

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testDelete() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("pl1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToLabels(Constants.TYPE, "pipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(null))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));

        when(argoKubernetesService.getWorkflowTemplate(anyString(),anyString())).thenReturn(workflowTemplate);
        when(dependencyHandlerService.pipelineHasDepends(any(WorkflowTemplate.class))).thenReturn(true);

        doNothing().when(argoKubernetesService).deleteWorkflowTemplate("projectId", "id");
        doNothing().when(argoKubernetesService).deleteWorkflow("projectId", "id");
        when(projectService.getParams("projectId")).thenReturn(ParamsDto.builder().params(new LinkedList<>()).build());

        pipelineService.delete("projectId", "id");

        verify(argoKubernetesService).deleteWorkflowTemplate("projectId", "id");
        verify(argoKubernetesService).deleteWorkflow("projectId", "id");
    }

    @Test
    void testDeletePipelineHasDependency() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("pl1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToLabels(Constants.TYPE, "pipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                .pipelineParams(new PipelineParams()
                        .dependentPipelineIds(List.of("pl2")))
                .templates(List.of(new Template()
                        .name(Constants.DAG_TEMPLATE_NAME)
                        .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate(anyString(),anyString())).thenReturn(workflowTemplate);

        assertThrows(BadRequestException.class, () -> pipelineService.delete("projectId", "pl1"),
                "Expected exception must be thrown");

    }

    @Test
    void testRun() {
        mockAuthenticationService();
        when(argoKubernetesService.getArgoExecutorLimitsCpu()).thenReturn("0.1");
        when(argoKubernetesService.getArgoExecutorLimitsMemory()).thenReturn("100Mi");
        when(argoKubernetesService.getArgoExecutorRequestsCpu()).thenReturn("0.1");
        when(argoKubernetesService.getArgoExecutorRequestsMemory()).thenReturn("50Mi");
        ResourceQuota resourceQuota = new ResourceQuota();
        ResourceQuotaStatus resourceQuotaStatus = new ResourceQuotaStatus();
        resourceQuotaStatus.setHard(Map.of(Constants.LIMITS_CPU,
                                           Quantity.parse("1"),
                                           Constants.LIMITS_MEMORY,
                                           Quantity.parse("1G"),
                                           Constants.REQUESTS_CPU,
                                           Quantity.parse("1"),
                                           Constants.REQUESTS_MEMORY,
                                           Quantity.parse("1G")));
        resourceQuota.setStatus(resourceQuotaStatus);
        when(argoKubernetesService.getResourceQuota("projectId", Constants.QUOTA_NAME)).thenReturn(resourceQuota);
        doNothing().when(argoKubernetesService).createOrReplaceWorkflow(eq("projectId"), any(Workflow.class));
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        DagTemplate dagTemplate = new DagTemplate();
        Arguments task1Args = new Arguments();
        task1Args.setParameters(List.of(new Parameter().name(PipelineService.LIMITS_CPU).value("0.1"),
                                        new Parameter().name(PipelineService.LIMITS_MEMORY).value("300Mi"),
                                        new Parameter().name(PipelineService.REQUESTS_CPU).value("0.1"),
                                        new Parameter().name(PipelineService.REQUESTS_MEMORY).value("300Mi")));
        Arguments task2Args = new Arguments();
        task2Args.setParameters(List.of(new Parameter().name(PipelineService.LIMITS_CPU).value("0.1"),
                                        new Parameter().name(PipelineService.LIMITS_MEMORY).value("100Mi"),
                                        new Parameter().name(PipelineService.REQUESTS_CPU).value("0.1"),
                                        new Parameter().name(PipelineService.REQUESTS_MEMORY).value("100Mi")));
        dagTemplate.setTasks(List.of(new DagTask().arguments(task1Args).template("test"),
                                     new DagTask().arguments(task2Args).template("test2")));
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(dagTemplate))));
        when(argoKubernetesService.getWorkflowTemplate(anyString(), anyString())).thenReturn(workflowTemplate);
        pipelineService.run("projectId", "id");
        verify(argoKubernetesService).createOrReplaceWorkflow(eq("projectId"), any(Workflow.class));
    }

    @Test
    void testSuspend() throws ApiException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate("projectId", "id")).thenReturn(workflowTemplate);
        WorkflowStatus status = new WorkflowStatus();
        status.setPhase("Running");
        Workflow workflow = new Workflow();
        workflow.setStatus(status);
        status.setStartedAt(DateTime.now());
        status.setFinishedAt(DateTime.now());
        status.setProgress("0/2");
        WorkflowSpec spec = new WorkflowSpec();
        spec.setSuspend(false);
        spec.setTemplates(new ArrayList<>());
        workflow.setSpec(spec);
        when(argoKubernetesService.getWorkflow("projectId", "id")).thenReturn(workflow);
        pipelineService.suspend("projectId", "id");
        verify(apiInstance).workflowServiceSuspendWorkflow("projectId", "id", new WorkflowSuspendRequest());
    }

    @Test
    void testSuspendFailure() throws ResourceNotFoundException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate("projectId", "id")).thenReturn(workflowTemplate);
        WorkflowStatus status = new WorkflowStatus();
        status.setPhase("Running");
        Workflow workflow = new Workflow();
        workflow.setStatus(status);
        status.setStartedAt(DateTime.now());
        status.setFinishedAt(DateTime.now());
        status.setProgress("0/2");
        WorkflowSpec spec = new WorkflowSpec();
        spec.setSuspend(true);
        spec.setTemplates(new ArrayList<>());
        workflow.setSpec(spec);
        when(argoKubernetesService.getWorkflow("projectId", "id")).thenReturn(workflow);
        assertThrows(BadRequestException.class,
                     () -> pipelineService.suspend("projectId", "id"),
                     "Expected exception must be thrown");
    }

    @Test
    void testResume() throws ApiException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate("projectId", "id")).thenReturn(workflowTemplate);
        WorkflowStatus status = new WorkflowStatus();
        status.setPhase(SUSPENDED_STATUS);
        Workflow workflow = new Workflow();
        workflow.setStatus(status);
        status.setStartedAt(DateTime.now());
        status.setFinishedAt(DateTime.now());
        status.setProgress("0/2");
        WorkflowSpec spec = new WorkflowSpec();
        spec.setSuspend(true);
        spec.setTemplates(new ArrayList<>());
        workflow.setSpec(spec);
        when(argoKubernetesService.getWorkflow("projectId", "id")).thenReturn(workflow);
        pipelineService.resume("projectId", "id");
        verify(apiInstance).workflowServiceResumeWorkflow("projectId", "id", new WorkflowResumeRequest());
    }

    @Test
    void testStop() throws ApiException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate("projectId", "id")).thenReturn(workflowTemplate);
        WorkflowStatus status = new WorkflowStatus();
        Workflow workflow = new Workflow();
        workflow.setStatus(status);
        status.setStartedAt(DateTime.now());
        status.setFinishedAt(DateTime.now());
        status.setProgress("0/2");
        status.setPhase(RUNNING_STATUS);
        WorkflowSpec spec = new WorkflowSpec();
        spec.setTemplates(new ArrayList<>());
        workflow.setSpec(spec);
        when(argoKubernetesService.getWorkflow("projectId", "id")).thenReturn(workflow);
        pipelineService.stop("projectId", "id");
        verify(apiInstance).workflowServiceStopWorkflow("projectId", "id", new WorkflowStopRequest());
    }

    @Test
    void testTerminate() throws ApiException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate("projectId", "id")).thenReturn(workflowTemplate);
        WorkflowStatus status = new WorkflowStatus();
        Workflow workflow = new Workflow();
        workflow.setStatus(status);
        workflow.setMetadata(new ObjectMetaBuilder()
                .withCreationTimestamp(DateTime.now().toString())
                .withName("id")
                .build());
        status.setStartedAt(DateTime.now());
        status.setFinishedAt(DateTime.now());
        status.setProgress("0/2");
        status.setPhase(RUNNING_STATUS);
        WorkflowSpec spec = new WorkflowSpec();
        spec.setTemplates(new ArrayList<>());
        workflow.setSpec(spec);
        when(argoKubernetesService.getWorkflow("projectId", "id")).thenReturn(workflow);
        when(argoKubernetesService.getCronWorkflowsByLabel("projectId", "id")).thenReturn(new ArrayList<>());
        pipelineService.terminate("projectId", "id");
        verify(apiInstance).workflowServiceTerminateWorkflow("projectId", "id", new WorkflowTerminateRequest());
    }

    @Test
    void testRetry() throws ApiException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate("projectId", "id")).thenReturn(workflowTemplate);
        WorkflowStatus status = new WorkflowStatus();
        Workflow workflow = new Workflow();
        workflow.setStatus(status);
        status.setStartedAt(DateTime.now());
        status.setFinishedAt(DateTime.now());
        status.setProgress("0/2");
        status.setPhase(FAILED_STATUS);
        WorkflowSpec spec = new WorkflowSpec();
        spec.setTemplates(new ArrayList<>());
        workflow.setSpec(spec);
        when(argoKubernetesService.getWorkflow("projectId", "id")).thenReturn(workflow);
        pipelineService.retry("projectId", "id");
        verify(apiInstance).workflowServiceRetryWorkflow("projectId", "id", new WorkflowRetryRequest());
    }

    @Test
    void testCreateCron() {
        CronPipelineDto cronPipelineDto = new CronPipelineDto();
        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceCronWorkflow(eq("projectId"), any(CronWorkflow.class));
        pipelineService.createCron("projectId", "id", cronPipelineDto);
        verify(argoKubernetesService).createOrReplaceCronWorkflow(eq("projectId"), any(CronWorkflow.class));
    }

    @Test
    void testDeleteCron() {
        doNothing().when(argoKubernetesService).deleteCronWorkflow("projectId", "id");
        pipelineService.deleteCron("projectId", "id");
        verify(argoKubernetesService).deleteCronWorkflow("projectId", "id");
    }

    @Test
    void testGetCronById() {
        CronPipelineDto expected = CronPipelineDto.builder().build();
        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setSpec(new CronWorkflowSpec());
        when(argoKubernetesService.getCronWorkflow("projectId", "id")).thenReturn(cronWorkflow);
        assertEquals(expected.getSchedule(),
                     pipelineService.getCronById("projectId", "id").getSchedule(),
                     "Schedule must be equals to expected");
    }

    @Test
    void testUpdateCron() {
        CronPipelineDto cronPipelineDto = new CronPipelineDto();
        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setSpec(new CronWorkflowSpec());

        doNothing()
                .when(argoKubernetesService)
                .createOrReplaceCronWorkflow(eq("projectId"), any(CronWorkflow.class));
        when(argoKubernetesService.getCronWorkflow("projectId", "id")).thenReturn(cronWorkflow);
        pipelineService.updateCron("projectId", "id", cronPipelineDto);
        verify(argoKubernetesService).createOrReplaceCronWorkflow(eq("projectId"), any(CronWorkflow.class));

        when(argoKubernetesService.getCronWorkflow("projectId", "id")).thenThrow(ResourceNotFoundException.class);
        assertThrows(BadRequestException.class, () -> pipelineService.updateCron("projectId", "id", cronPipelineDto),
                "Expected exception must be thrown");
    }

    @Test
    void testGetLastStartedWorkflow() {
        Workflow workflow1 = new Workflow();
        workflow1.setMetadata(new ObjectMetaBuilder()
                .withCreationTimestamp("2022-09-01T18:10:21Z")
                .withName("wf1")
                .build());

        Workflow workflow2 = new Workflow();
        workflow2.setMetadata(new ObjectMetaBuilder()
                .withCreationTimestamp("2022-09-02T18:10:21Z")
                .withName("wf2")
                .build());

        Workflow workflow3 = new Workflow();
        workflow3.setMetadata(new ObjectMetaBuilder()
                .withCreationTimestamp("2022-09-03T18:10:21Z")
                .withName("wf3")
                .build());

        List<Workflow> workflowList = new ArrayList<>();
        workflowList.add(workflow1);
        workflowList.add(workflow2);
        workflowList.add(workflow3);
        Workflow actual = pipelineService.getLastStartedWorkflow(workflowList);

        assertEquals("wf3", actual.getMetadata().getName(),
                "Name must be equals to expected");
    }

    @Test
    void testGetDefinition() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("pl1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToLabels(Constants.TYPE, "pipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String(GRAPH.toString().getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());

        JsonNode response = dependencyHandlerService.getDefinition(workflowTemplate);
        assertEquals(GRAPH, response, "Name must be equals to expected");
    }

    @Test
    void testGetDefinitionException() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                .withName("pl1")
                .addToLabels(Constants.NAME, "testPipeline")
                .addToLabels(Constants.TYPE, "pipeline")
                .addToAnnotations(Constants.DEFINITION,
                        Base64.encodeBase64String("test".getBytes()))
                .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                .build());
        assertThrows(JsonParseException.class, () -> dependencyHandlerService.getDefinition(workflowTemplate),
                "Expected exception must be thrown");
    }

    @Test
    void testGetPipelineHistory() {
        Map<String, PipelineHistory> pipelineHistories = new HashMap<>();
        pipelineHistories.put("1", new PipelineHistory(
                "3b6d29b1-f717-4532-8fb6-68b339932253",
                "pipeline",
                "2022-11-11 11:02:23",
                "2022-11-11 11:03:23",
                "test",
                "Succeeded",
                List.of("3b6d29b1-f717-4532-8fb6-68b339932253-sadsada")
        ));

        Map<String, PipelineNodeHistory> pipelineNodeHistories = new HashMap<>();
        pipelineNodeHistories.put("2", new PipelineNodeHistory(
                "3b6d29b1-f717-4532-8fb6-68b339932253-sadsada",
                "name",
                "JOB",
                "2022-11-11 11:02:23",
                "2022-11-11 11:03:23",
                "Succeeded"));

        when(pipelineHistoryRepository.findAll(
                String.format("%s:%s_%s",
                        PIPELINE_HISTORY,
                        "projectId",
                        "id")
        )).thenReturn(pipelineHistories);
        when(pipelineHistoryRepository.findAll(
                String.format("%s:%s_%s_%s",
                        PIPELINE_NODE_HISTORY,
                        "projectId",
                        "3b6d29b1-f717-4532-8fb6-68b339932253-sadsada",
                        "1")
        )).thenReturn(pipelineNodeHistories);

        List<PipelineHistoryResponseDto> pipelineHistory = pipelineService
                .getPipelineHistory("projectId", "id");

        List<PipelineNodesHistoryResponseDto> pipelineNodesHistoryResponseDtos = new ArrayList<>();

        pipelineNodesHistoryResponseDtos.add(
                new PipelineNodesHistoryResponseDto(
                        "3b6d29b1-f717-4532-8fb6-68b339932253-sadsada",
                        "name",
                        "JOB",
                        "2022-11-11 11:02:23",
                        "2022-11-11 11:03:23",
                        "Succeeded",
                        "2"));

        PipelineHistoryResponseDto expected = PipelineHistoryResponseDto
                .builder()
                .id("3b6d29b1-f717-4532-8fb6-68b339932253")
                .type("pipeline")
                .startedAt("2022-11-11 11:02:23")
                .finishedAt("2022-11-11 11:03:23")
                .startedBy("test")
                .status("Succeeded")
                .nodes(pipelineNodesHistoryResponseDtos)
                .build();

        assertEquals(expected, pipelineHistory.get(0), "Pipeline history must be equal to expected");
    }

    private void mockAuthenticationService() {
        UserInfo ui = new UserInfo();
        ui.setSuperuser(true);
        when(authenticationService.getUserInfo()).thenReturn(ui);
    }
}
