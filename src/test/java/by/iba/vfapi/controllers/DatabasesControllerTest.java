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

import by.iba.vfapi.dto.databases.PingStatusDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.DatabasesService;
import by.iba.vfapi.services.auth.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabasesControllerTest {

    @Mock
    private DatabasesService databasesService;
    @Mock
    private AuthenticationService authenticationService;
    @Mock
    private RestTemplate restTemplate;
    private DatabasesController controller;

    @BeforeEach
    public void setUp() {
        controller = new DatabasesController(databasesService, authenticationService,
                restTemplate, "host");
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationService.getUserInfo()).thenReturn(expected);
    }

    @Test
    void testPing() {
        String projectId = "project";
        String connectionName = "con";
        ConnectDto dto = Mockito.mock(ConnectDto.class);
        when(databasesService.getConnection(projectId, connectionName)).thenReturn(dto);
        when(restTemplate.postForEntity("host", dto, PingStatusDto.class)).
                thenReturn(new ResponseEntity<>(PingStatusDto.builder().status(true).build(),HttpStatus.OK));
        ResponseEntity<PingStatusDto> actual = controller.ping(projectId, connectionName);
        assertEquals(Boolean.TRUE, Objects.requireNonNull(actual.getBody()).isStatus(), "Ping() should return true!");
        assertEquals(HttpStatus.OK, actual.getStatusCode(), "Ping() should return 200 code!");
        verify(databasesService).getConnection(projectId, connectionName);
        verify(restTemplate).postForEntity("host", dto, PingStatusDto.class);
    }

    @Test
    void testPingWithParams() {
        String projectId = "project";
        ConnectDto dto = Mockito.mock(ConnectDto.class);
        when(databasesService.replaceParams(projectId, dto)).thenReturn(dto);
        when(restTemplate.postForEntity("host", dto, PingStatusDto.class)).
                thenReturn(new ResponseEntity<>(PingStatusDto.builder().status(true).build(),HttpStatus.OK));
        ResponseEntity<PingStatusDto> actual = controller.ping(projectId, dto);
        assertEquals(Boolean.TRUE, Objects.requireNonNull(actual.getBody()).isStatus(), "Ping() should return true!");
        assertEquals(HttpStatus.OK, actual.getStatusCode(), "Ping() should return 200 code!");
        verify(restTemplate).postForEntity("host", dto, PingStatusDto.class);
    }
}
