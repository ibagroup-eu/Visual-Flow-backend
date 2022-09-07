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

package by.iba.vfapi.controllers;

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.exporting.ExportRequestDto;
import by.iba.vfapi.dto.exporting.ExportResponseDto;
import by.iba.vfapi.dto.importing.ImportRequestDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.DagTemplate;
import by.iba.vfapi.model.argo.Template;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import by.iba.vfapi.services.TransferService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {
    @Mock
    private TransferService transferService;
    @InjectMocks
    private TransferController transferController;

    @Test
    void testExporting() {
        ConfigMap configMap = new ConfigMap();
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        when(transferService.exporting("projectId",
                                       Set.of("jobId1", "jobId2"),
                                       Set.of(new ExportRequestDto.PipelineRequest("pipelineId1", true))))
            .thenReturn(ExportResponseDto
                            .builder()
                            .jobs(Set.of(configMap))
                            .pipelines(Set.of(workflowTemplate))
                            .build());

        ExportResponseDto response = transferController.exporting("projectId",
                                                                  new ExportRequestDto(Set.of("jobId1", "jobId2"),
                                                                                       Set.of(new ExportRequestDto.PipelineRequest(
                                                                                           "pipelineId1",
                                                                                           true))));
        ExportResponseDto exportResponseDto =
            ExportResponseDto.builder().jobs(Set.of(configMap)).pipelines(Set.of(workflowTemplate)).build();

        assertEquals(exportResponseDto, response);
    }

    @Test
    void testImporting() {
        ConfigMap e1 = new ConfigMap();
        Set<ConfigMap> jsonJobs = Set.of(e1);
        WorkflowTemplate e11 = new WorkflowTemplate();
        Set<WorkflowTemplate> jsonPipelines = Set.of(e11);
        when(transferService.importing("projectId", jsonJobs, jsonPipelines))
            .thenReturn(ImportResponseDto
                            .builder()
                            .notImportedJobs(List.of())
                            .notImportedPipelines(List.of())
                            .build());

        ImportResponseDto importing =
            transferController.importing("projectId", new ImportRequestDto(List.of(e11), List.of(e1)));

        ImportResponseDto expected =
            ImportResponseDto.builder().notImportedJobs(List.of()).notImportedPipelines(List.of()).build();
        assertEquals(expected, importing);
        verify(transferService).importing("projectId", jsonJobs, jsonPipelines);
    }

    @Test
    void testImportingNotUniqueJobs() {
        ImportRequestDto dto = new ImportRequestDto(List.of(), List.of(new ConfigMap(), new ConfigMap()));
        assertThrows(BadRequestException.class, () -> transferController.importing("projectId", dto));
    }

    @Test
    void testImportingNotUniquePipelines() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        ImportRequestDto dto = new ImportRequestDto(List.of(workflowTemplate, workflowTemplate), List.of());
        assertThrows(BadRequestException.class, () -> transferController.importing("projectId", dto));
    }

    @Test
    void testCheckAccessToImport() {
        String projectId = "projectId";
        when(transferService.checkImportAccess(projectId)).thenReturn(true);
        assertTrue(transferController.checkAccessToImport(projectId).isAccess());
        verify(transferService).checkImportAccess(anyString());
    }

    @Test
    void testCopyJob() {
        String projectId = "projectId";
        String jobId = "jobId";
        transferController.copyJob(projectId, jobId);
        verify(transferService).copyJob(anyString(), anyString());
    }

    @Test
    void testCopyPipeline() {
        String projectId = "projectId";
        String pipelineId = "pipelineId";
        transferController.copyPipeline(projectId, pipelineId);
        verify(transferService).copyPipeline(anyString(), anyString());
    }
}