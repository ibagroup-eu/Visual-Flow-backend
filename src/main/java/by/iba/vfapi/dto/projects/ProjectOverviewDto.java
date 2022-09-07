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
import by.iba.vfapi.dto.Constants;
import io.fabric8.kubernetes.api.model.Namespace;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Project overview DTO class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "DTO with essential project info")
public class ProjectOverviewDto {
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_NAME)
    private String name;
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_ID)
    private String id;
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_DESCRIPTION)
    private String description;
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_LOCK_STATUS)
    private boolean isLocked;

    /**
     * Converts Namespace to Project DTO.
     *
     * @param namespace namespace to convert.
     * @return project dto builder.
     */
    public static ProjectOverviewDto fromNamespace(Namespace namespace, boolean isLocked) {
        return ProjectOverviewDto
            .builder()
            .name(namespace.getMetadata().getAnnotations().get(Constants.NAME_FIELD))
            .description(namespace.getMetadata().getAnnotations().get(Constants.DESCRIPTION_FIELD))
            .id(namespace.getMetadata().getName())
            .isLocked(isLocked)
            .build();
    }
}
