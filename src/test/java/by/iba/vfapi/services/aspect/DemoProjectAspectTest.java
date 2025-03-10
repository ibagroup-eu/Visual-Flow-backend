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
package by.iba.vfapi.services.aspect;

import by.iba.vfapi.controllers.ProjectController;
import by.iba.vfapi.dto.projects.DemoLimitsDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.services.ArgoKubernetesService;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.ProjectService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;
import java.util.Map;

import static by.iba.vfapi.dto.Constants.TYPE;
import static by.iba.vfapi.dto.Constants.TYPE_JOB;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoProjectAspectTest {

    private static final String PROJECT_ID = "vf-test";
    private static final String ENTITY_NAME = "test-obj";
    @Mock
    private ProjectService projectService;
    private DemoProjectAspect aspectService;
    private ProjectController projectController;
    @Mock
    private KubernetesService kubernetesService;
    @Mock
    private ArgoKubernetesService argoKubernetesService;
    @Mock
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        aspectService = new DemoProjectAspect(projectService, kubernetesService, argoKubernetesService);
        projectController = new ProjectController(projectService, authenticationService);
    }

    @Test
    void testCheckJobLimitExecWithErrorAlreadyExists() {
        when(projectService.get(PROJECT_ID)).thenReturn(ProjectResponseDto.builder()
                .demo(true)
                .demoLimits(DemoLimitsDto.builder().jobsNumAllowed(1).build())
                .build());
        ConfigMap jobCm = new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(ENTITY_NAME)
                        .withLabels(Map.of(TYPE, TYPE_JOB))
                        .build())
                .build();
        when(kubernetesService.getAllConfigMaps(PROJECT_ID)).thenReturn(List.of(new ConfigMap()));
        when(kubernetesService.isConfigMapReadable(PROJECT_ID, ENTITY_NAME)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> aspectService.checkJobLimitCondition(PROJECT_ID, jobCm),
                "Method should throw an exception, since the project already has one job, and limit is " +
                        "one job.");
    }

    @Test
    void testCheckJobLimitExecWithErrorLimitBigger() {
        when(projectService.get(PROJECT_ID)).thenReturn(ProjectResponseDto.builder()
                .demo(true)
                .demoLimits(DemoLimitsDto.builder().jobsNumAllowed(1).build())
                .build());
        ConfigMap jobCm = new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(ENTITY_NAME)
                        .build())
                .build();
        when(kubernetesService.getAllConfigMaps(PROJECT_ID)).thenReturn(List.of(new ConfigMap(), new ConfigMap()));

        assertThrows(BadRequestException.class, () -> aspectService.checkJobLimitCondition(PROJECT_ID, jobCm),
                "Method should throw an exception, since the project already has two jobs, and limit is " +
                        "one job.");
        verify(kubernetesService, never()).isConfigMapReadable(PROJECT_ID, ENTITY_NAME);
    }

    @Test
    void testCheckPipelineLimitExecWithErrorAlreadyExists() {
        when(projectService.get(PROJECT_ID)).thenReturn(ProjectResponseDto.builder()
                .demo(true)
                .demoLimits(DemoLimitsDto.builder().pipelinesNumAllowed(1).build())
                .build());
        WorkflowTemplate pipelineCm = new WorkflowTemplate();
        pipelineCm.setMetadata(new ObjectMetaBuilder()
                .withName(ENTITY_NAME)
                .build());
        when(argoKubernetesService.getAllWorkflowTemplates(PROJECT_ID)).thenReturn(List.of(new WorkflowTemplate()));
        when(argoKubernetesService.isWorkflowTemplateReadable(PROJECT_ID, ENTITY_NAME)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> aspectService.checkPipelinesLimitCondition(PROJECT_ID, pipelineCm),
                "Method should throw an exception, since the project already has one pipeline, and limit is " +
                        "one pipeline.");
    }

    @Test
    void testCheckPipelineLimitExecWithErrorLimitBigger() {
        when(projectService.get(PROJECT_ID)).thenReturn(ProjectResponseDto.builder()
                .demo(true)
                .demoLimits(DemoLimitsDto.builder().pipelinesNumAllowed(1).build())
                .build());
        WorkflowTemplate pipelineCm = new WorkflowTemplate();
        pipelineCm.setMetadata(new ObjectMetaBuilder()
                .withName(ENTITY_NAME)
                .build());
        when(argoKubernetesService.getAllWorkflowTemplates(PROJECT_ID)).thenReturn(List.of(new WorkflowTemplate(),
                new WorkflowTemplate()));

        assertThrows(BadRequestException.class, () -> aspectService.checkPipelinesLimitCondition(PROJECT_ID, pipelineCm),
                "Method should throw an exception, since the project already has two pipelines, and limit is " +
                        "one pipeline.");
        verify(argoKubernetesService, never()).isWorkflowTemplateReadable(PROJECT_ID, ENTITY_NAME);
    }

    @Test
    void testCheckExpirationDateExecWithError() {
        AspectJProxyFactory factory = new AspectJProxyFactory(projectController);
        factory.addAspect(aspectService);
        when(projectService.get(PROJECT_ID)).thenReturn(ProjectResponseDto.builder()
                .locked(true)
                .build());
        ProjectController proxy = factory.getProxy();

        assertThrows(BadRequestException.class, () -> proxy.get(PROJECT_ID),
                "Method should throw an exception, since the project is locked.");
    }
}
