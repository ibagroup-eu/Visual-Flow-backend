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

package by.iba.vfapi.dto.history;

import by.iba.vfapi.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Pipeline history response DTO class.
 */
@EqualsAndHashCode
@Builder
@Getter
@RequiredArgsConstructor
@ToString
public class PipelineHistoryResponseDto {
    @Schema(description = "Pipeline id")
    private final String id;
    @Schema(ref = OpenApiConfig.SCHEMA_TYPE)
    private final String type;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private final String startedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_SECOND)
    private final String finishedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_USER)
    private final String startedBy;
    @Schema(ref = OpenApiConfig.SCHEMA_PIPELINE_STATUS)
    private final String status;
    @Schema(ref = OpenApiConfig.SCHEMA_PIPELINE_NODES_HISTORY)
    private final List<PipelineNodesHistoryResponseDto> nodes;
}