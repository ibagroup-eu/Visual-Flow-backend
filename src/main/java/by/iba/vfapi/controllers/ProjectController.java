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

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ConnectionsDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ProjectOverviewListDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.ProjectService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

/**
 * Project controller class.
 */
@Slf4j
@Tag(name = "Project API", description = "Manage projects")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
@Validated
public class ProjectController {
    private final ProjectService projectService;
    private final AuthenticationService authenticationService;

    /**
     * Creates new project.
     *
     * @param projectDto object that contains initial data
     * @return ResponseEntity with status code
     */
    @Operation(summary = "Create a new project", description = "Create a new empty project")
    @PostMapping
    @ApiResponse(responseCode = "201", description = "Id of a new project", content = {@Content(schema =
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_ID))})
    public ResponseEntity<String> create(@RequestBody @Valid final ProjectRequestDto projectDto) {
        LOGGER.info(
                "{} - Creating project",
                authenticationService.getFormattedUserInfo());
        String id = projectService.create(projectDto);
        LOGGER.info(
                "{} - Project '{}' successfully created",
                authenticationService.getFormattedUserInfo(),
                id);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * Gets project by id.
     *
     * @param projectId project id.
     * @return ResponseEntity with status code and project date (ProjectDto).
     */
    @Operation(summary = "Get a project", description = "Get information about the project by it's id")
    @GetMapping("/{projectId}")
    public ProjectResponseDto get(@PathVariable final String projectId) {
        LOGGER.info(
                "{} - Receiving project '{}' ",
                authenticationService.getFormattedUserInfo(),
                projectId);
        return projectService.get(projectId);
    }

    /**
     * Gets projects list.
     *
     * @return project list.
     */
    @Operation(summary = "Get list with all projects", description = "Get list with all projects that you have " +
            "access to")
    @GetMapping
    public ProjectOverviewListDto getAll() {
        LOGGER.info(
                "{} - Receiving list of projects",
                authenticationService.getFormattedUserInfo());
        return projectService.getAll();
    }

    /**
     * Change project params.
     *
     * @param projectId  project id.
     * @param projectDto new project params.
     */
    @Operation(summary = "Update the project", description = "Update existing project by providing new " +
            "name/description/quota")
    @PostMapping("/{projectId}")
    public void update(
            @PathVariable final String projectId, @RequestBody @Valid final ProjectRequestDto projectDto) {
        LOGGER.info(
                "{} - Updating project '{}'",
                authenticationService.getFormattedUserInfo(),
                projectId);
        projectService.update(projectId, projectDto);
        LOGGER.info(
                "{} - Project '{}' description and resource quota successfully updated",
                authenticationService.getFormattedUserInfo(),
                projectId);
    }

    /**
     * Deletes project by id.
     *
     * @param projectId project id.
     * @return ResponseEntity with 204 status code.
     */
    @Operation(summary = "Delete the project", description = "Delete existing project with all related " +
            "pipelines/jobs", responses = {@ApiResponse(responseCode = "204", description = "Indicates successful " +
            "project deletion")})
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable final String projectId) {
        LOGGER.info(
                "{} - Deleting project '{}'",
                authenticationService.getFormattedUserInfo(),
                projectId);
        projectService.delete(projectId);
        LOGGER.info(
                "{} - Project '{}' successfully deleted",
                authenticationService.getFormattedUserInfo(),
                projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Gets project resource utilization.
     *
     * @param projectId project id.
     * @return project usage info.
     */
    @Operation(summary = "Get project resource utilization", description = "Get resource utilization by " +
            "observing k8s pod metrics and quota")
    @GetMapping("/{projectId}/usage")
    public ResourceUsageDto getUsage(@PathVariable String projectId) {
        LOGGER.info(
                "{} - Receiving project '{}' resource utilization",
                authenticationService.getFormattedUserInfo(),
                projectId);
        return projectService.getUsage(projectId);
    }

    /**
     * Gets params for given project.
     *
     * @param projectId project id.
     * @return project parameters.
     */
    @Operation(summary = "Get all project params", description = "Fetch all params for given project")
    @GetMapping("/{projectId}/params")
    public ParamsDto getParams(@PathVariable final String projectId) {
        LOGGER.info(
                "{} - Receiving params for the '{}' project",
                authenticationService.getFormattedUserInfo(),
                projectId);
        return projectService.getParams(projectId);
    }

    /**
     * Create a param for given project.
     *
     * @param projectId is a project id.
     * @param paramKey is the created parameter key.
     * @param newParamDto is the created parameter DTO.
     */
    @Operation(summary = "Create a project param", description = "Create a parameter for given project")
    @PostMapping("/{projectId}/params/{paramKey}")
    public void createParam(@PathVariable final String projectId, @PathVariable String paramKey,
                            @RequestBody @Valid ParamDto newParamDto) {
        LOGGER.info(
                "{} - Creating {} param for project '{}'",
                authenticationService.getFormattedUserInfo(), paramKey, projectId);
        ParamDto paramDto = projectService.createParam(projectId, paramKey, newParamDto);
        LOGGER.info(
                "{} - The {} param for the '{}' project has been successfully created",
                authenticationService.getFormattedUserInfo(), paramKey, projectId);
        ResponseEntity.ok(paramDto);
    }

    /**
     * Update a param for given project.
     *
     * @param projectId is a project id.
     * @param paramKey is the updated parameter key.
     * @param newParamDto is the updated parameter DTO.
     */
    @Operation(summary = "Update a project param", description = "Update a parameter for given project")
    @PutMapping("/{projectId}/params/{paramKey}")
    public void updateParam(@PathVariable final String projectId, @PathVariable String paramKey,
                            @RequestBody @Valid ParamDto newParamDto) {
        LOGGER.info(
                "{} - Updating {} param for project '{}'",
                authenticationService.getFormattedUserInfo(), paramKey, projectId);
        ParamDto paramDto = projectService.updateParam(projectId, paramKey, newParamDto);
        LOGGER.info(
                "{} - The {} param for the '{}' project has been successfully updated",
                authenticationService.getFormattedUserInfo(), paramKey, projectId);
        ResponseEntity.ok(paramDto);
    }

    /**
     * Delete a param for given project.
     *
     * @param projectId is a project id.
     * @param paramKey is the parameter key should be deleted.
     */
    @Operation(summary = "Delete a project param", description = "Delete a parameter for given project")
    @DeleteMapping("/{projectId}/params/{paramKey}")
    public void deleteParam(@PathVariable final String projectId, @PathVariable String paramKey) {
        LOGGER.info(
                "{} - Deleting {} param for project '{}'",
                authenticationService.getFormattedUserInfo(), paramKey, projectId);
        projectService.deleteParam(projectId, paramKey);
        LOGGER.info(
                "{} - The {} param for the '{}' project has been successfully deleted",
                authenticationService.getFormattedUserInfo(), paramKey, projectId);
    }

    /**
     * Gets connections for given project.
     *
     * @param projectId project id.
     * @return project connections.
     */
    @Operation(summary = "Get all project connections", description = "Fetch all connections for given project")
    @GetMapping("/{projectId}/connections")
    public ConnectionsDto getConnections(@PathVariable final String projectId) {
        LOGGER.info(
                "{} - Receiving connections for the '{}' project",
                authenticationService.getFormattedUserInfo(),
                projectId);
        return projectService.getConnections(projectId);
    }

    /**
     * Gets a connection for given project by name.
     *
     * @param projectId   project id.
     * @param id connection's id.
     * @return project connection.
     */
    @Operation(summary = "Get information about connection",
               description = "Fetch specific connection for given project")
    @GetMapping("/{projectId}/connection/{id}")
    public ResponseEntity<ConnectDto> getConnection(@PathVariable final String projectId,
                                                    @PathVariable final String id) {
        LOGGER.info(
                "{} - Receiving {} connection for the '{}' project",
                authenticationService.getFormattedUserInfo(),
                id,
                projectId);
        ConnectDto connection = projectService.getConnectionById(projectId, id);
        if (connection == null) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(connection);
        }
    }

    /**
     * Creates a new connection for given project.
     *
     * @param projectId project's id.
     * @param connectDto list of connections to update.
     */
    @Operation(summary = "Create new connection", description = "Create a connection " +
            "for given project")
    @PostMapping("/{projectId}/connection")
    public ResponseEntity<String> createConnection(
            @PathVariable final String projectId, @RequestBody @Valid final ConnectDto connectDto) {
        String name = connectDto.getValue().get(Constants.CONNECTION_NAME_LABEL).textValue();
        LOGGER.info(
                "{} - Creating {} connection for project '{}'",
                authenticationService.getFormattedUserInfo(), name, projectId);
        ConnectDto connection = projectService.createConnection(projectId, connectDto);
        LOGGER.info(
                "{} - The {} connection for the '{}' project has been successfully updated",
                authenticationService.getFormattedUserInfo(), name, projectId);

        return ResponseEntity.status(HttpStatus.CREATED).body(connection.getKey());
    }

    /**
     * Updates a connection for given project.
     *
     * @param projectId         project projectId.
     * @param id       connection's id.
     * @param connectDto list of connections to update.
     */
    @Operation(summary = "Update existing connection", description = "Update a connection " +
            "for given project")
    @PutMapping("/{projectId}/connections/{id}")
    public void updateConnection(
            @PathVariable final String projectId, @PathVariable final String id,
            @RequestBody @Valid ConnectDto connectDto) {
        LOGGER.info(
                "{} - Updating {} connection for project '{}'",
                authenticationService.getFormattedUserInfo(), id, projectId);
        connectDto.setKey(id);
        ConnectDto connection = projectService.updateConnection(projectId, id, connectDto);
        LOGGER.info(
                "{} - The {} connection for the '{}' project has been successfully updated",
                authenticationService.getFormattedUserInfo(), id, projectId);
        ResponseEntity.ok(connection);
    }

    /**
     * Deletes a connection for given project.
     *
     * @param projectId project's id.
     * @param id connection's id.
     */
    @Operation(summary = "Delete the connection", description = "Delete connection " +
            "for given project")
    @DeleteMapping("/{projectId}/connections/{id}")
    public ResponseEntity<ConnectDto> deleteConnection(
            @PathVariable final String projectId, @PathVariable final String id) {
        LOGGER.info(
                "{} - Deleting connection for project '{}'",
                authenticationService.getFormattedUserInfo(),
                projectId);
        projectService.deleteConnection(projectId, id);
        LOGGER.info(
                "{} - The {} connection for the '{}' project has been deleted",
                authenticationService.getFormattedUserInfo(),
                id,
                projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Applies access grants for given project.
     *
     * @param projectId   project id.
     * @param accessTable user - role map.
     */
    @Operation(summary = "Manage project access grants", description = "Replace existing access grants with " +
            "provided ones")
    @PostMapping("/{projectId}/users")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The updated version of project grants " +
            "that will replace old one. Note that usernames must point to existing users and roles must be defined " +
            "as Cluster roles", content = {@Content(schema = @Schema(ref =
            OpenApiConfig.SCHEMA_PROJECT_ACCESS_GRANTS))})
    public void applyAccessTable(
            @PathVariable final String projectId, @RequestBody final Map<String, String> accessTable) {
        LOGGER.info(
                "{} - Applying access grants for the project '{}'",
                authenticationService.getFormattedUserInfo(),
                projectId);
        String currentUser = authenticationService.getUserInfo().map(UserInfo::getUsername).orElseThrow();
        projectService.createAccessTable(projectId, accessTable, currentUser);
        LOGGER.info(
                "{} - Grants for project '{}' successfully applied",
                authenticationService.getFormattedUserInfo(),
                projectId);
    }

    /**
     * Retrieves access grants for given project.
     *
     * @param projectId project id.
     * @return user - role map.
     */
    @Operation(summary = "Get project access grants", description = "Fetch all users with roles that were " +
            "assigned to them in given project")
    @GetMapping("/{projectId}/users")
    public AccessTableDto getAccessTable(@PathVariable final String projectId) {
        LOGGER.info(
                "{} - Receiving access grants table for the project '{}'",
                authenticationService.getFormattedUserInfo(),
                projectId);
        return projectService.getAccessTable(projectId);
    }

    /**
     * Method for recalculating all params using in all connections. It may take some time to make a full
     * recalculation.
     *
     * @param projectId is a project id.
     * @return true, if recalculation completed successfully.
     */
    @Operation(summary = "Recalculate params usages for connections", description = "Recalculates usages of all" +
            " params in all connections", hidden = true)
    @PostMapping("/{projectId}/recalc/cons")
    public boolean recalculateParamsConUsages(@PathVariable final String projectId) {
        LOGGER.info(
                "{} - Recalculation params connections usages for the project '{}'",
                authenticationService.getFormattedUserInfo(),
                projectId);
        return projectService.recalculateParamsConUsages(projectId);
    }
}
