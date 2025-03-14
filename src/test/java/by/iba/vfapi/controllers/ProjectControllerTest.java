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

import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ConnectionsDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ProjectOverviewDto;
import by.iba.vfapi.dto.projects.ProjectOverviewListDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.dto.projects.ResourceQuotaRequestDto;
import by.iba.vfapi.dto.projects.ResourceQuotaResponseDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.ProjectService;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectService projectService;
    @Spy
    private AuthenticationService authenticationService = new AuthenticationService();
    @InjectMocks
    private ProjectController controller;

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
    void testGetProjectList() {
        ProjectOverviewListDto expected = ProjectOverviewListDto.builder().projects(List.of(
                ProjectOverviewDto.builder().name("name 1").build(),
                ProjectOverviewDto.builder().name("name 2").build())).editable(true).build();
        when(projectService.getAll()).thenReturn(expected);
        ProjectOverviewListDto actual = controller.getAll();
        assertEquals(expected, actual, "Project list must be equals to expected");
        verify(projectService).getAll();
    }

    @Test
    void testGetUtilization() {
        String name = "name";
        ResourceUsageDto usageDto = ResourceUsageDto.builder().build();

        when(projectService.getUsage(name)).thenReturn(usageDto);

        ResourceUsageDto result = controller.getUsage(name);

        assertEquals(usageDto, result, "Utilization must be equals to usageDto");
        verify(projectService).getUsage(name);
    }

    @Test
    void testUpdate() {
        ProjectRequestDto projectDto = ProjectRequestDto.builder().build();
        controller.update("test", projectDto);
        verify(projectService).update("test", projectDto);
    }

    @Test
    void testDelete() {
        String name = "name";
        doNothing().when(projectService).delete(name);
        assertEquals(ResponseEntity.status(HttpStatus.NO_CONTENT).build(),
                controller.delete(name),
                "Status must be 204");

        verify(projectService).delete(name);
    }

    @Test
    void testGetParams() {
        String name = "name";
        List<ParamDto> paramDto = List.of(ParamDto.builder().build());
        ParamsDto paramsDto = ParamsDto.builder().params(paramDto).build();

        when(projectService.getParams(name)).thenReturn(paramsDto);


        ParamsDto result = controller.getParams(name);
        assertEquals(paramsDto, result, "Params must be equals to paramsDto");
        verify(projectService).getParams(name);
    }

    @Test
    void testCreateParam() {
        ParamDto newParam = ParamDto.builder().build();
        controller.createParam("vf", "key", newParam);
        verify(projectService).createParam("vf", "key", newParam);
    }

    @Test
    void testUpdateParam() {
        ParamDto newParam = ParamDto.builder().build();
        controller.updateParam("vf", "key", newParam);
        verify(projectService).updateParam("vf", "key", newParam);
    }

    @Test
    void testDeleteParam() {
        controller.deleteParam("vf", "key");
        verify(projectService).deleteParam("vf", "key");
    }

    @Test
    void testGetConnections() {
        String name = "name";
        List<ConnectDto> connections = List.of(ConnectDto.builder().build());
        ConnectionsDto connectionsDto = ConnectionsDto.builder().connections(connections).build();
        when(projectService.getConnections(name)).thenReturn(connectionsDto);
        ConnectionsDto result = controller.getConnections(name);
        assertEquals(connectionsDto, result, "Connections must be equals to connectionsDto");
        verify(projectService).getConnections(name);
    }

    @Test
    void testGetConnection() {
        ConnectDto connection = ConnectDto.builder().key("test").build();
        ResponseEntity<ConnectDto> responseErr = controller.getConnection("test", "name1");
        assertEquals(HttpStatus.NOT_FOUND, responseErr.getStatusCode(), "Status must be 404");

        when(projectService.getConnectionById("test", "name1")).thenReturn(connection);

        ResponseEntity<ConnectDto> response = controller.getConnection("test", "name1");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Status must be 200");
    }

    @Test
    void testCreateConnection() throws JsonProcessingException {
        ConnectDto connection = ConnectDto.builder().key("test")
                .value(new ObjectMapper().readTree("{\"myname\": \"#ok#\", \"name3\": \"value3\", " +
                "\"connectionName\": \"connectionValue\"}")).build();

        when(projectService.createConnection("projectId", connection)).thenReturn(connection);
        ResponseEntity<String> response = controller.createConnection("projectId", connection);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "Status must be OK");
        assertEquals("test", response.getBody(), "Body must be equals to test value");

        verify(projectService).createConnection(anyString(), any());
    }

    @Test
    void testUpdateConnection() {
        ConnectDto connection = ConnectDto.builder().key("test").build();
        controller.updateConnection("test", "name", connection);
        verify(projectService).updateConnection("test", "name", connection);
    }

    @Test
    void testDeleteConnection() {
        doNothing().when(projectService).deleteConnection("projectName", "name");
        ResponseEntity<ConnectDto> response = controller.deleteConnection("projectName", "name");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "Status must be 204");
        verify(projectService).deleteConnection(anyString(), anyString());
    }

    @Test
    void testCreateProject() {
        String name = "name";
        String description = "description";

        ResourceQuotaRequestDto dto = ResourceQuotaRequestDto.builder().limitsCpu(5f).limitsMemory(10f).build();
        ProjectRequestDto projectDto =
                ProjectRequestDto.builder().name(name).description(description).limits(dto).build();

        when(projectService.create(projectDto)).thenReturn(name);

        ResponseEntity<String> result = controller.create(projectDto);

        assertEquals(HttpStatus.CREATED, result.getStatusCode(), "Status must be OK");
        assertEquals(name, result.getBody(), "Body must be equals to name");

        verify(projectService).create(projectDto);
    }

    @Test
    void testGetById() {
        String name = "name";
        String description = "description";
        Namespace namespace = new Namespace();
        ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setName(name);
        objectMeta.setAnnotations(Map.of(description, description));
        namespace.setMetadata(objectMeta);
        ProjectResponseDto expected = ProjectResponseDto
                .builder()
                .name(name)
                .description(description)
                .limits(ResourceQuotaResponseDto.builder().limitsMemory(10f).limitsCpu(10f).build())
                .build();
        when(projectService.get(name)).thenReturn(expected);

        ProjectResponseDto response = controller.get(name);

        assertEquals(expected, response, "Response must be equals to expected");
        verify(projectService).get(name);
    }

    @Disabled("Temporarily disabled, needs investigation")
    @Test
    void testApplyAccessTable() {
        Map<String, String> accessTable = new HashMap<>();
        accessTable.put("name1", "admin");
        accessTable.put("name2", "viewer");
        doNothing().when(projectService).createAccessTable("name", accessTable, "name1");
        when(authenticationService.getUserInfo()).thenReturn(Optional.of(new UserInfo("9",
                "name",
                "name1",
                "email@gomel.iba.by",
                true)));

        controller.applyAccessTable("name", accessTable);

        verify(projectService).createAccessTable("name", accessTable, "name1");
        verifyNoMoreInteractions(projectService);
    }

    @Test
    void testGetAccessTable() {
        Map<String, String> accessTable = new HashMap<>();
        accessTable.put("name1", "admin");
        accessTable.put("name2", "viewer");
        AccessTableDto accessTableDto = AccessTableDto.builder().grants(accessTable).build();
        when(projectService.getAccessTable("name")).thenReturn(accessTableDto);
        AccessTableDto actual = controller.getAccessTable("name");
        assertEquals(accessTableDto, actual, "AccessTable must be equals to accessTableDto");
        verify(projectService).getAccessTable("name");
        verifyNoMoreInteractions(projectService);
    }
}
