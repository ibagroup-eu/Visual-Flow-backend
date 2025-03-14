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

import by.iba.vfapi.dto.history.PipelineHistoryResponseDto;
import by.iba.vfapi.dto.history.PipelineNodesHistoryResponseDto;
import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import by.iba.vfapi.dto.pipelines.PipelineDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewListDto;
import by.iba.vfapi.model.argo.PipelineParams;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.PipelineService;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {
    @Mock
    private PipelineService pipelineService;
    private PipelineController pipelineController;
    @Spy
    private AuthenticationService authenticationService = new AuthenticationService();

    @BeforeEach
    void setUp() {
        pipelineController = new PipelineController(pipelineService, authenticationService);
        UserInfo expected = new UserInfo();
        expected.setName("name");
        expected.setId("id");
        expected.setUsername("username");
        expected.setEmail("email");
        when(authenticationService.getUserInfo()).thenReturn(Optional.of(expected));
    }

    @Test
    void testCreate() throws JsonProcessingException {
        when(pipelineService.create("projectId", null,
                new ObjectMapper().readTree("{\"graph\":[]}"), new PipelineParams()))
            .thenReturn("id");
        JsonNode graph = new ObjectMapper().readTree("{\"graph\":[]}");
        PipelineParams params = new PipelineParams();
        ResponseEntity<String> response =
            pipelineController.create("projectId", new PipelineDto(graph, true, params));

        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "Status must be OK");
        assertEquals("id", response.getBody(), "Body must be equals to Id");
    }

    @Test
    void testGet() throws IOException {
        PipelineDto dto = PipelineDto.builder()
                .lastModified("lastModified")
                .name("name")
                .definition(new ObjectMapper().readTree("{\"graph\":[]}".getBytes()))
                .build();

        when(pipelineService.getById("projectId", "id")).thenReturn(dto);
        PipelineDto response = pipelineController.get("projectId", "id");

        assertEquals(dto, response, "Response must be equals to dto");
    }

    @Test
    void testUpdate() throws JsonProcessingException {
        doNothing()
            .when(pipelineService)
            .update("projectName", "name", null, new PipelineParams(), new ObjectMapper().readTree("{\"graph\":[]}")
            );
        JsonNode graph = new ObjectMapper().readTree("{\"graph\":[]}");
        PipelineParams params = new PipelineParams();
        pipelineController.update("projectName", "name", new PipelineDto(graph, true, params));

        verify(pipelineService).update(anyString(), anyString(), isNull(), any(PipelineParams.class), any(JsonNode.class));
    }

    @Test
    void testDelete() {
        doNothing().when(pipelineService).delete("projectName", "name");
        ResponseEntity<Void> response = pipelineController.delete("projectName", "name");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "Status must be 204");
        verify(pipelineService).delete(anyString(), anyString());
    }

    @Test
    void testGetAll() {
        when(pipelineService.getAll("project1"))
            .thenReturn(PipelineOverviewListDto
                            .builder()
                            .pipelines(List.of(new PipelineOverviewDto(), new PipelineOverviewDto()))
                            .editable(true)
                            .build());

        PipelineOverviewListDto response = pipelineController.getAll("project1");

        assertEquals(2, response.getPipelines().size(), "Size must be equals to 2");
        assertTrue(response.isEditable(), "Must be true");

        verify(pipelineService).getAll(anyString());
    }

    @Test
    void testRun() {
        doNothing().when(pipelineService).run("projectId", "id");

        pipelineController.run("projectId", "id");

        verify(pipelineService).run(anyString(), anyString());
    }

    @Test
    void testStop() {
        doNothing().when(pipelineService).stop("projectId", "id");

        pipelineController.stop("projectId", "id");

        verify(pipelineService).stop(anyString(), anyString());
    }

    @Test
    void testResume() {
        doNothing().when(pipelineService).resume("projectId", "id");

        pipelineController.resume("projectId", "id");

        verify(pipelineService).resume(anyString(), anyString());
    }

    @Test
    void testTerminate() {
        doNothing().when(pipelineService).terminate("projectId", "id");

        pipelineController.terminate("projectId", "id");

        verify(pipelineService).terminate(anyString(), anyString());
    }

    @Test
    void testRetry() {
        doNothing().when(pipelineService).retry("projectId", "id");

        pipelineController.retry("projectId", "id");

        verify(pipelineService).retry(anyString(), anyString());
    }

    @Test
    void testSuspend() {
        doNothing().when(pipelineService).suspend("projectId", "id");

        pipelineController.suspend("projectId", "id");

        verify(pipelineService).suspend(anyString(), anyString());
    }

    @Test
    void testCreateCron() {

        CronPipelineDto cronPipelineDto = new CronPipelineDto();
        doNothing().when(pipelineService).createCron("projectId", "id", cronPipelineDto);

        pipelineController.createCron("projectId", "id", cronPipelineDto);

        verify(pipelineService).createCron(anyString(), anyString(), any(CronPipelineDto.class));
    }

    @Test
    void testDeleteCron() {
        doNothing().when(pipelineService).deleteCron("projectId", "id");

        ResponseEntity<Void> response = pipelineController.deleteCron("projectId", "id");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "Status must be 204");
        verify(pipelineService).deleteCron(anyString(), anyString());
    }

    @Test
    void testGetCronPipeline() {
        CronPipelineDto dto = CronPipelineDto.builder().build();

        when(pipelineService.getCronById("projectId", "id")).thenReturn(dto);
        CronPipelineDto response = pipelineController.getCronPipeline("projectId", "id");

        assertEquals(dto, response, "Response must be equals to dto");
    }

    @Test
    void testUpdateCron() {
        CronPipelineDto cronPipelineDto = new CronPipelineDto();
        doNothing().when(pipelineService).updateCron("projectId", "id", cronPipelineDto);

        pipelineController.updateCron("projectId", "id", cronPipelineDto);

        verify(pipelineService).updateCron(anyString(), anyString(), any(CronPipelineDto.class));
    }

    @Test
    void testGetPipelineHistory() {
        List<PipelineHistoryResponseDto> pipelineHistoryResponseDtos = new ArrayList<>();
        List<PipelineNodesHistoryResponseDto> pipelineNodesHistoryResponseDtos = new ArrayList<>();

        pipelineNodesHistoryResponseDtos.add(
                new PipelineNodesHistoryResponseDto(
                        "1",
                        "node",
                        "JOB",
                        "Success",
                        "2022-11-11 11:02:23",
                        "2022-11-11 11:02:23",
                        "212213"));

        pipelineHistoryResponseDtos.add(new PipelineHistoryResponseDto(
                "1",
                "pipeline",
                "2022-11-11 11:03:23",
                "2022-11-11 11:03:23",
                "test",
                "Success",
                pipelineNodesHistoryResponseDtos));

        when(pipelineService.getPipelineHistory("projectId", "id")).thenReturn(pipelineHistoryResponseDtos);

        pipelineController.getPipelineHistory("projectId", "id");
        verify(pipelineService).getPipelineHistory(anyString(), anyString());
    }

    @Test
    void testCopy() {
        doNothing().when(pipelineService).copy("project1", "jobId");
        pipelineController.copy("project1", "jobId");
        verify(pipelineService).copy(anyString(), anyString());
    }
}
