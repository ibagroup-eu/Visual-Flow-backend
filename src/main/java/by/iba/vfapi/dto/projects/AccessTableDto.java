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
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Access table dto class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "DTO with all access grants for specific project")
public class AccessTableDto {
    public static final String USERNAME = "username";

    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_ACCESS_GRANTS)
    private Map<String, String> grants;
    @Schema(description = "Whether current user can modify access grants")
    private boolean editable;

    /**
     * Converts RoleBindings to AccessTable DTO.
     *
     * @param roleBindings RoleBindings to convert.
     * @return AccessTable dto builder.
     */
    public static AccessTableDtoBuilder fromRoleBindings(Collection<RoleBinding> roleBindings) {
        Map<String, String> grants = roleBindings.stream().collect(Collectors.toMap(
            roleBinding -> roleBinding.getMetadata().getAnnotations().get(USERNAME),
            roleBinding -> roleBinding.getRoleRef().getName()));
        return AccessTableDto.builder().grants(grants);
    }

    /**
     * Converts AccessTable DTO to RoleBindings.
     *
     * @return RoleBindings list.
     */
    public List<RoleBinding> toRoleBindings() {
        return grants.entrySet().stream().map((Map.Entry<String, String> entry) -> {
            String username = entry.getKey();
            String role = entry.getValue();
            return new RoleBindingBuilder()
                .withNewMetadata()
                .addToAnnotations(AccessTableDto.USERNAME, username)
                .withName(String.format("%s-%s", username, role))
                .endMetadata()
                .addNewSubject()
                .withKind("ServiceAccount")
                .withName(username)
                .endSubject()
                .withNewRoleRef()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("ClusterRole")
                .withName(role)
                .endRoleRef()
                .build();
        }).collect(Collectors.toList());
    }
}
