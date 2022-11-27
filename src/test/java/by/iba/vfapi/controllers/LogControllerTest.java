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

import by.iba.vfapi.dto.LogDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.LogService;
import by.iba.vfapi.services.auth.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogControllerTest {
    @Mock
    private AuthenticationService authenticationServiceMock;
    @Mock
    private LogService logService;
    private LogController controller;

    @BeforeEach
    void setUp() {
        controller = new LogController(authenticationServiceMock, logService);
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationServiceMock.getUserInfo()).thenReturn(expected);
    }


    @Test
    void testGetLogs() {
        List<LogDto> logDtoList = new ArrayList<>();
        logDtoList.add(LogDto
                .builder()
                .message("Message")
                .level("INFO")
                .timestamp("2022-11-11 11:02:23,180")
                .build());

        when(logService.getParsedPodLogs("project1", "jobId")).thenReturn(logDtoList);
        List<LogDto> response = controller.getLogs("project1", "jobId");
        assertEquals(logDtoList, response, "Response must be equal to dto");
        verify(logService).getParsedPodLogs(anyString(), anyString());
    }

    @Test
    void testGetLogsHistory() {
        List<LogDto> logDtoList = new ArrayList<>();
        logDtoList.add(LogDto
                .builder()
                .message("Message")
                .level("INFO")
                .timestamp("2022-11-11 11:02:23,180")
                .build());

        when(logService.getParsedHistoryLogs("project1", "jobId", "logId")).thenReturn(logDtoList);
        List<LogDto> response = controller.getLogsHistory("project1", "jobId", "logId");
        assertEquals(logDtoList, response, "Response must be equal to dto");
        verify(logService).getParsedHistoryLogs(anyString(), anyString(), anyString());
    }

    @Test
    void testGetCustomContainerLogs() {
        List<LogDto> logDtoList = new ArrayList<>();
        logDtoList.add(LogDto
                .builder()
                .message("Message")
                .level("INFO")
                .timestamp("2022-11-11 11:02:23,180")
                .build());

        when(logService.getCustomContainerLogs("project1", "jobId", "nodeId"))
                .thenReturn(logDtoList);
        List<LogDto> response = controller.getCustomContainerLogs("project1", "jobId", "nodeId");
        assertEquals(logDtoList, response, "Response must be equal to dto");
        verify(logService).getCustomContainerLogs(anyString(), anyString(), anyString());
    }

}
