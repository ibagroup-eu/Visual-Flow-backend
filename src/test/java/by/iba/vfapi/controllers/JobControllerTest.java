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

import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobRequestDto;
import by.iba.vfapi.dto.jobs.JobResponseDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.JobService;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {
    @Mock
    private JobService jobService;
    @Mock
    private AuthenticationService authenticationServiceMock;

    private JobController controller;

    @BeforeEach
    void setUp() {
        controller = new JobController(jobService, authenticationServiceMock);
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationServiceMock.getUserInfo()).thenReturn(expected);
    }

    @Test
    void testGetAll() {
        when(jobService.getAll("project1")).thenReturn(JobOverviewListDto
                                                           .builder()
                                                           .jobs(List.of(JobOverviewDto
                                                                             .builder()
                                                                             .pipelineInstances(List.of())
                                                                             .build(),
                                                                         JobOverviewDto
                                                                             .builder()
                                                                             .pipelineInstances(List.of())
                                                                             .build()))
                                                           .editable(true)
                                                           .build());

        JobOverviewListDto response = controller.getAll("project1");
        assertEquals(2, response.getJobs().size(), "Jobs size must be 2");
        assertTrue(response.isEditable(), "Must be true");

        verify(jobService).getAll(anyString());
    }

    @Test
    void testCreate() throws JsonProcessingException {
        JobRequestDto jobRequestDto = JobRequestDto
            .builder()
            .definition(new ObjectMapper().readTree("{\"graph\":[]}"))
            .name("newName")
            .params(Map.of("param1", "value1"))
            .build();
        when(jobService.create("projectId", jobRequestDto)).thenReturn("jobId");
        ResponseEntity<String> response = controller.create("projectId", jobRequestDto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "Status must be OK");
        assertEquals("jobId", response.getBody(), "Body must be equals to jobId");

        verify(jobService).create(anyString(), any());
    }

    @Test
    void testUpdate() throws JsonProcessingException {
        JobRequestDto jobRequestDto = JobRequestDto
            .builder()
            .definition(new ObjectMapper().readTree("{\"graph\":[]}"))
            .name("newName")
            .params(Map.of("param1", "value1"))
            .build();
        doNothing().when(jobService).update("jobId", "projectId", jobRequestDto);

        controller.update("projectId", "jobId", jobRequestDto);

        verify(jobService).update(anyString(), anyString(), any());
    }

    @Test
    void testGet() throws IOException {
        JobResponseDto dto = JobResponseDto
            .builder()
            .lastModified("lastModified")
            .definition(new ObjectMapper().readTree("{\"graph\":[]}".getBytes()))
            .name("name")
            .build();

        when(jobService.get("project1", "jobId")).thenReturn(dto);

        JobResponseDto response = controller.get("project1", "jobId");

        assertEquals(dto, response, "Response must be equal to dto");

        verify(jobService).get(anyString(), anyString());
    }

    @Test
    void testDelete() {
        doNothing().when(jobService).delete("project1", "jobId");

        ResponseEntity<Void> response = controller.delete("project1", "jobId");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "Status must be 204");

        verify(jobService).delete(anyString(), anyString());
    }

    @Test
    void testRun() {
        doNothing().when(jobService).run("project1", "jobId");
        controller.run("project1", "jobId");
        verify(jobService).run(anyString(), anyString());
    }

    @Test
    void testStop() {
        doNothing().when(jobService).stop("project1", "jobId");
        controller.stop("project1", "jobId");
        verify(jobService).stop(anyString(), anyString());
    }
}
