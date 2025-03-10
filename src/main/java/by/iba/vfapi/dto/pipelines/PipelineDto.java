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

package by.iba.vfapi.dto.pipelines;

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.model.argo.PipelineParams;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Pipeline response DTO class.
 * Contains the basic information from {@link PipelineOverviewDto},
 * as well as the graph and parameters.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Schema(description = "DTO with basic information about pipeline that's extended with it's definition")
public class PipelineDto extends PipelineOverviewDto {
    @Schema(ref = OpenApiConfig.SCHEMA_PIPELINE_DEFINITION)
    private JsonNode definition;
    @Schema(description = "Whether a current user can modify the pipeline")
    private boolean editable;
    @Schema(ref = OpenApiConfig.SCHEMA_PIPELINE_PARAMETERS)
    private PipelineParams params;
}
