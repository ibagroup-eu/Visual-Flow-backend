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

import by.iba.vfapi.dto.exporting.ExportRequestDto;
import by.iba.vfapi.dto.exporting.ExportResponseDto;
import by.iba.vfapi.dto.importing.ImportRequestDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.dto.pipelines.PipelineDto;
import by.iba.vfapi.services.TransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        JobDto configMap = new JobDto();
        PipelineDto workflowTemplate = new PipelineDto();
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
        JobDto e1 = new JobDto();
        List<JobDto> jsonJobs = List.of(e1);
        PipelineDto e11 = new PipelineDto();
        List<PipelineDto> jsonPipelines = List.of(e11);
        when(transferService.importing("projectId", jsonJobs, jsonPipelines))
                .thenReturn(ImportResponseDto
                        .builder()
                        .notImportedJobs(List.of())
                        .notImportedPipelines(List.of())
                        .build());

        ResponseEntity<ImportResponseDto> importing =
                transferController.importing("projectId", new ImportRequestDto(List.of(e11), List.of(e1)));

        ResponseEntity<ImportResponseDto> expected = new ResponseEntity<>(
                ImportResponseDto.builder().notImportedJobs(List.of()).notImportedPipelines(List.of()).build(),
                HttpStatus.OK);
        assertEquals(expected, importing);
        verify(transferService).importing("projectId", jsonJobs, jsonPipelines);
    }

    @Test
    void testImportingNotUniqueJobs() {
        ImportRequestDto dto = new ImportRequestDto(List.of(), List.of(new JobDto(), new JobDto()));
        when(transferService.importing("projectId", dto.getJobs(), dto.getPipelines()))
                .thenReturn(ImportResponseDto.builder().notImportedJobs(List.of("test")).build());
        assertEquals(HttpStatus.BAD_REQUEST, transferController.importing("projectId", dto).getStatusCode(),
                "Importing not unique jobs should return Bad Request");
    }

    @Test
    void testImportingNotUniquePipelines() {
        PipelineDto workflowTemplate = PipelineDto.builder()
                .id("id")
                .name("name")
                .lastModified("lastModified")
                .build();
        ImportRequestDto dto = new ImportRequestDto(List.of(workflowTemplate, workflowTemplate), List.of());
        when(transferService.importing("projectId", dto.getJobs(), dto.getPipelines()))
                .thenReturn(ImportResponseDto.builder().notImportedPipelines(List.of("name")).build());
        assertEquals(HttpStatus.BAD_REQUEST, transferController.importing("projectId", dto).getStatusCode(),
                "Importing not unique pipelines should return Bad Request");
    }

    @Test
    void testCheckAccessToImport() {
        String projectId = "projectId";
        when(transferService.checkImportAccess(projectId)).thenReturn(true);
        assertTrue(transferController.checkAccessToImport(projectId).isAccess());
        verify(transferService).checkImportAccess(anyString());
    }
}