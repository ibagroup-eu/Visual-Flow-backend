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

package by.iba.vfapi.dto.importing;

import by.iba.vfapi.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * Export response DTO class
 */
@Getter
@Builder
@EqualsAndHashCode
@ToString
@Schema(description = "DTO that contains jobs/pipelines that were not imported")
public class ImportResponseDto {
    // TODO normal ids or Kube ones
    @ArraySchema(arraySchema = @Schema(description = "Ids of the jobs that were not imported"), schema =
    @Schema(ref = OpenApiConfig.SCHEMA_KUBE_UUID_ONE))
    private final List<String> notImportedJobs;
    @ArraySchema(arraySchema = @Schema(description = "Ids of the pipelines that were not imported"), schema =
    @Schema(ref = OpenApiConfig.SCHEMA_KUBE_UUID_TWO))
    private final List<String> notImportedPipelines;
    private final Map<String, List<String>> errorsInJobs;
    private final Map<String, List<String>> errorsInPipelines;
    private final Map<String, List<MissingParamDto>> missingProjectParams;
    private final Map<String, List<MissingParamDto>> missingProjectConnections;
}
