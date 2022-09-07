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

package by.iba.vfapi.dto.exporting;

import by.iba.vfapi.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Export request DTO class.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "DTO that stores ids of the jobs and pipelines that will be exported")
public class ExportRequestDto {
    @NotNull
    @ArraySchema(arraySchema = @Schema(description = "Ids of the jobs that will be exported"), schema =
    @Schema(ref = OpenApiConfig.SCHEMA_UUID_ONE))
    private Set<String> jobIds;
    @NotNull
    @Schema(description = "list of pipeline DTOs that will be exported")
    private Set<PipelineRequest> pipelines;

    /**
     * Export pipeline object.
     */
    @EqualsAndHashCode
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @Schema(description = "DTO for pipeline that will be imported")
    public static class PipelineRequest {
        @Schema(description = "Id of the pipeline", implementation = UUID.class)
        private String pipelineId;
        @Schema(description = "Whether to include pipeline jobs into export")
        private boolean withRelatedJobs;
    }
}
