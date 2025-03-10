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
import by.iba.vfapi.dto.importing.ImportAccessDto;
import by.iba.vfapi.dto.importing.ImportRequestDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.services.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * Transfer controller class.
 */

@Tag(name = "Import/Export/Copy API", description = "Import/Export/Copy jobs and pipelines")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
public class TransferController {
    private final TransferService transferService;

    /**
     * Export.
     *
     * @param projectId        project id
     * @param exportRequestDto dto with job ids and pipelines for export
     * @return object with exported jobs and pipelines
     */
    @Operation(summary = "Export pipelines/jobs", description = "Export existing pipelines/jobs into JSON file")
    @PostMapping("{projectId}/exportResources")
    public ExportResponseDto exporting(
            @PathVariable String projectId, @RequestBody @Valid ExportRequestDto exportRequestDto) {
        return transferService.exporting(projectId, exportRequestDto.getJobIds(), exportRequestDto.getPipelines());
    }

    /**
     * Import.
     *
     * @param projectId        project id
     * @param importRequestDto dto with jobs ids and pipelines ids for export
     * @return object witch contains not imported ids of pipelines and jobs
     */
    @Operation(summary = "Import pipelines/jobs", description = "Import pipelines/jobs into a specific project " +
            "from JSON structure")
    @PostMapping("{projectId}/importResources")
    public ResponseEntity<ImportResponseDto> importing(
            @PathVariable String projectId, @RequestBody @Valid ImportRequestDto importRequestDto) {
        ImportResponseDto result = transferService.importing(projectId, importRequestDto.getJobs(),
                importRequestDto.getPipelines());
        HttpStatus resultStatus;
        if (CollectionUtils.isEmpty(result.getNotImportedJobs()) &&
                CollectionUtils.isEmpty(result.getNotImportedPipelines())) {
            resultStatus = HttpStatus.OK;
        } else {
            resultStatus = HttpStatus.BAD_REQUEST;
        }
        return new ResponseEntity<>(result, resultStatus);
    }

    /**
     * Get access flag.
     *
     * @param projectId project id
     * @return pipeline flag
     */
    @Operation(summary = "Check import access", description = "Check import access for specific project")
    @GetMapping(value = "{projectId}/checkAccess")
    public ImportAccessDto checkAccessToImport(@PathVariable String projectId) {
        return new ImportAccessDto(transferService.checkImportAccess(projectId));
    }
}
