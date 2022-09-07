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
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceFluent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Project request DTO class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "DTO with project's name, description and quota limits")
public class ProjectRequestDto {
    @Pattern(regexp = Constants.NAME_PATTERN)
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_NAME)
    private String name;
    @NotNull
    @Size(max = Constants.MAX_DESCRIPTION_LENGTH)
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_DESCRIPTION)
    private String description;
    @NotNull
    @Valid
    @Schema(description = "Project's quota information")
    private ResourceQuotaRequestDto limits;

    /**
     * Converts Project DTO to namespace.
     *
     * @return namespace builder.
     */
    public NamespaceBuilder toNamespace(String id, Map<String, String> customAnnotations) {
        NamespaceFluent.MetadataNested<NamespaceBuilder> namespaceBuilderMetadataNested =
            new NamespaceBuilder().withNewMetadata().withName(id);
        if (!customAnnotations.isEmpty()) {
            namespaceBuilderMetadataNested.addToAnnotations(customAnnotations);
        }
        return namespaceBuilderMetadataNested
            .addToAnnotations(Constants.NAME_FIELD, name)
            .addToAnnotations(Constants.DESCRIPTION_FIELD, description)
            .endMetadata();
    }
}
