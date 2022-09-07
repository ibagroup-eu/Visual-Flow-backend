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

package by.iba.vfapi.controllers;

import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.UserService;
import by.iba.vfapi.services.auth.AuthenticationService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private AuthenticationService authenticationServiceMock;
    @Mock
    private UserService userServiceMock;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(authenticationServiceMock, userServiceMock);
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationServiceMock.getUserInfo()).thenReturn(expected);
    }

    @Test
    void testWhoAmI() {
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationServiceMock.getUserInfo()).thenReturn(expected);
        UserInfo actual = controller.whoAmI();
        assertEquals(expected, actual, "UserInfo must be equals to expected");
        verify(authenticationServiceMock, times(2)).getUserInfo();
    }

    @Test
    void testGetUsers() {
        List<Map<String, String>> expected = new ArrayList<>();
        Map<String, String> annotations = new HashMap<>();
        annotations.put("username", "IvanShautsou");
        annotations.put("id", "22");
        annotations.put("name", "Ivan");
        expected.add(annotations);
        when(userServiceMock.getUsers()).thenReturn(expected);
        List<Map<String, String>> actual = controller.getUsers();
        assertEquals(expected, actual, "List of users must be equals to expected");
        verify(userServiceMock).getUsers();
        verifyNoMoreInteractions(userServiceMock);
    }

    @Test
    void testGetRoles() {
        List<String> expected = new ArrayList<>();
        expected.add("admin");
        expected.add("viewer");
        when(userServiceMock.getRoleNames()).thenReturn(expected);
        List<String> actual = controller.getRoles();
        assertEquals(expected, actual, "List of roles must be equals to expected");
        verify(userServiceMock).getRoleNames();
        verifyNoMoreInteractions(userServiceMock);
    }
}
