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

import by.iba.vfapi.dto.DataSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.Future;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static by.iba.vfapi.config.OpenApiConfig.DATASOURCES_FOR_DEMO;

/**
 * Demo Limits DTO. Filled, if {@link ProjectResponseDto#isDemo()} is true.
 * Contains information about limits for project, such as jobs count limit,
 * pipelines count limit and sources for stages, which should be shown on
 * UI.
 */
@Data
@Schema(description = "DTO for DEMO project's limits")
@Builder
public class DemoLimitsDto {
    @NotNull
    @NotEmpty
    @Schema(ref = DATASOURCES_FOR_DEMO)
    private Map<String, List<DataSource>> sourcesToShow;
    @Positive
    private int jobsNumAllowed;
    @Positive
    private int pipelinesNumAllowed;
    @NotNull
    @Future
    private LocalDate expirationDate;
    private boolean editable;
}
