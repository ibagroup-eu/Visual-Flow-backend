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
package by.iba.vfapi.services.utils;

import by.iba.vfapi.dto.projects.AccessTableDto;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class AccessTableUtils {

    public static final String USERNAME = "username";

    /**
     * Converts RoleBindings to AccessTable DTO.
     *
     * @param roleBindings RoleBindings to convert.
     * @return AccessTable dto builder.
     */
    public static AccessTableDto.AccessTableDtoBuilder fromRoleBindings(Collection<RoleBinding> roleBindings) {
        return AccessTableDto.builder().grants(roleBindings.stream().collect(Collectors.toMap(
                roleBinding -> roleBinding.getMetadata().getAnnotations().get(USERNAME),
                roleBinding -> roleBinding.getRoleRef().getName())));
    }

    /**
     * Converts AccessTable DTO to RoleBindings.
     *
     * @return RoleBindings list.
     */
    public List<RoleBinding> toRoleBindings(AccessTableDto accessTableDto) {
        return accessTableDto.getGrants().entrySet().stream().map((Map.Entry<String, String> entry) -> {
            String username = entry.getKey();
            String role = entry.getValue();
            return new RoleBindingBuilder()
                    .withNewMetadata()
                    .addToAnnotations(USERNAME, username)
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
