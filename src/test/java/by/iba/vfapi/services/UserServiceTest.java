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

package by.iba.vfapi.services;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private KubernetesService kubernetesService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(kubernetesService);
    }

    @Test
    void testGetUsers() {
        ServiceAccount account =
            new ServiceAccountBuilder().withNewMetadata().addToAnnotations("test", "test").endMetadata().build();
        when(kubernetesService.getServiceAccounts()).thenReturn(List.of(account));

        List<Map<String, String>> result = userService.getUsers();

        assertEquals(List.of(Map.of("test", "test")), result, "Users must be equal to expected");
        verify(kubernetesService).getServiceAccounts();
    }

    @Test
    void testGetRoleNames() {
        String roleName = "roleName";
        ClusterRole role = new ClusterRoleBuilder().withNewMetadata().withName(roleName).endMetadata().build();
        when(kubernetesService.getRoles()).thenReturn(List.of(role));

        List<String> result = userService.getRoleNames();

        assertEquals(List.of(roleName), result, "RoleNames must be equal to expected");
        verify(kubernetesService).getRoles();
    }
}
