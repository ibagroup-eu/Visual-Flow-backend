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

import by.iba.vfapi.dto.history.HistoryResponseDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.model.JobParams;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.JobService;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {
    @Mock
    private JobService jobService;
    @Spy
    private AuthenticationService authenticationService = new AuthenticationService();
    @InjectMocks
    private JobController controller;

    @BeforeEach
    void setUp() {
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationService.getUserInfo()).thenReturn(Optional.of(expected));
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
        JobDto jobDto = JobDto
            .builder()
            .definition(new ObjectMapper().readTree("{\"graph\":[]}"))
            .name("newName")
            .params(new JobParams().driverCores("1"))
            .build();
        when(jobService.create("projectId", jobDto)).thenReturn("jobId");
        ResponseEntity<String> response = controller.create("projectId", jobDto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "Status must be OK");
        assertEquals("jobId", response.getBody(), "Body must be equals to jobId");

        verify(jobService).create(anyString(), any());
    }

    @Test
    void testUpdate() throws JsonProcessingException {
        JobDto jobDto = JobDto
            .builder()
            .definition(new ObjectMapper().readTree("{\"graph\":[]}"))
            .name("newName")
            .params(new JobParams().driverCores("1"))
            .build();
        doNothing().when(jobService).update("jobId", "projectId", jobDto);

        controller.update("projectId", "jobId", jobDto);

        verify(jobService).update(anyString(), anyString(), any());
    }

    @Test
    void testGet() throws IOException {
        JobDto dto = JobDto
            .builder()
            .lastModified("lastModified")
            .definition(new ObjectMapper().readTree("{\"graph\":[]}".getBytes()))
            .name("name")
            .build();

        when(jobService.getById("project1", "jobId")).thenReturn(dto);

        JobDto response = controller.get("project1", "jobId");

        assertEquals(dto, response, "Response must be equal to dto");

        verify(jobService).getById(anyString(), anyString());
    }

    @Test
    void testDelete() {
        doNothing().when(jobService).delete("project1", "jobId");

        ResponseEntity<Void> response = controller.delete("project1", "jobId");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "Status must be 204");

        verify(jobService).delete(anyString(), anyString());
    }
    @Test
    void testJobHistory() {
        List<HistoryResponseDto> dtoList = List.of(HistoryResponseDto
                .builder()
                .id("3b6d29b1-f717-4532-8fb6-68b339932253")
                .type("job")
                .status("Succeeded")
                .startedAt("2022-08-24T09:45:09Z")
                .finishedAt("2022-08-24T09:46:19Z")
                .startedBy("jane-doe")
                .build());
        when(jobService.getJobHistory("projectId", "jobId")).thenReturn(dtoList);

        List<HistoryResponseDto> response = controller.getHistory("projectId", "jobId");

        assertEquals(dtoList, response, "Response must be equal to dto");

        verify(jobService).getJobHistory(anyString(), anyString());
    }

    @Test
    void testRun() {
        String expected = "podId";
        doReturn(expected).when(jobService).run("project1", "jobId", false);
        ResponseEntity<String> response = controller.run("project1", "jobId", false);
        assertEquals(expected, response.getBody(), "response should match");
        verify(jobService).run("project1", "jobId", false);
    }

    @Test
    void testStop() {
        doNothing().when(jobService).stop("project1", "jobId");
        controller.stop("project1", "jobId");
        verify(jobService).stop(anyString(), anyString());
    }

    @Test
    void testCopy() {
        doNothing().when(jobService).copy("project1", "jobId");
        controller.copy("project1", "jobId");
        verify(jobService).copy(anyString(), anyString());
    }
}
