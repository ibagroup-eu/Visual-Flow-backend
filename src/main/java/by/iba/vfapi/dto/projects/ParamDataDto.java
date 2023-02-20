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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * Param's value field DTO class.
 * Includes fields for identification parameter usages in different resources:
 * connections, jobs, pipelines.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Schema(description = "DTO that represents value field of the project param")
public class ParamDataDto {
    @NotNull
    @Schema(description = "Parameter's value. Note that leading/trailing spaces will be removed automatically",
            example = "sample text")
    private String text;
    @NotNull
    @Schema(description = "Parameter's usages in connections. Contains connections keys, in which the param is used.")
    private Set<String> conUsages;
    @NotNull
    @Schema(description = "Parameter's usages in jobs. Contains jobs ids, in which the param is used.")
    private Set<String> jobUsages;
    @NotNull
    @Schema(description = "Parameter's usages in pipelines. Contains pipelines ids, in which the param is used.")
    private Set<String> pipUsages;
}
