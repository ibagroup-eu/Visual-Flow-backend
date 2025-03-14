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

package by.iba.vfapi.dto.projects;

import by.iba.vfapi.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Resource quota response DTO class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "DTO with quota info")
public class ResourceQuotaResponseDto {
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_LIMIT_CPU)
    private Float limitsCpu;
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_REQUIRE_CPU)
    private Float requestsCpu;
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_LIMIT_MEMORY)
    private Float limitsMemory;
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_REQUIRE_MEMORY)
    private Float requestsMemory;
    @Schema(description = "Whether quota modification is allowed")
    private boolean editable;
}
