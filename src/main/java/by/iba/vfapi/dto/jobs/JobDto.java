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

package by.iba.vfapi.dto.jobs;

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.model.JobParams;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Single job response DTO class.
 * It contains basic meta information about jobs, as well as a graph, parameters.
 * It is the main DTO for transmitting information about a job.
 * In contrast of {@link JobOverviewDto}, it provides more detailed information.
 * Ignores null fields.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobDto {
    @Schema(ref = OpenApiConfig.SCHEMA_UUID_ONE)
    private String id;
    @Schema(description = "Job's name", example = "test_Job1")
    private String name;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_DEFINITION)
    private JsonNode definition;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private String startedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_SECOND)
    private String finishedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private String lastModified;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_PARAMS)
    private JobParams params;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_STATUS)
    private String status;
    @Schema(description = "Whether current user can run the job and whether the job has some stages in it")
    private boolean runnable;
    @Schema(description = "Whether current user can edit the job.")
    private boolean editable;
    @Schema(description = "Run ID.")
    private String runId;
    @Schema(description = "Run mode.")
    private String mode;
}
