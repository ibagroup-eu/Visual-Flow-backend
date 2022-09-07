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
import by.iba.vfapi.dto.LogDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobRequestDto;
import by.iba.vfapi.dto.jobs.JobResponseDto;
import by.iba.vfapi.services.JobService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Job controller class.
 */
@Slf4j
@Tag(name = "Job API", description = "Manage jobs")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
public class JobController {
    private final JobService jobService;
    private final AuthenticationService authenticationService;

    /**
     * Get all jobs in project.
     *
     * @param projectId project id
     * @return ResponseEntity with jobs graphs
     */
    @Operation(summary = "Get all jobs in a project", description = "Get information about all jobs in a project")
    @GetMapping("{projectId}/job")
    public JobOverviewListDto getAll(@PathVariable String projectId) {
        LOGGER.info(
            "{} - Receiving all jobs in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            projectId);
        //TODO replace full method body on "return jobService.getAll(projectId)" and remove method transform
        JobOverviewListDto jobs = jobService.getAll(projectId);

        return JobOverviewDto.withPipelineJobs(jobs);
    }

    /**
     * Creating new job in project.
     *
     * @param projectId     project id
     * @param jobRequestDto object with name and graph
     * @return ResponseEntity with id of new job
     */
    @Operation(summary = "Create a new job", description = "Create a new job in the project", responses =
        {@ApiResponse(responseCode = "200", description = "Id of a new job", content = @Content(schema =
        @Schema(ref = OpenApiConfig.SCHEMA_UUID_ONE)))})
    @PostMapping("{projectId}/job")
    public ResponseEntity<String> create(
        @PathVariable String projectId, @Valid @RequestBody JobRequestDto jobRequestDto) {
        LOGGER.info(
            "{} - Creating new job in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            projectId);
        String id = jobService.create(projectId, jobRequestDto);
        LOGGER.info(
            "{} - Job '{}' in project '{}' successfully created",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * Updating job in project by id.
     *
     * @param projectId     project id
     * @param id            job id
     * @param jobRequestDto object with name and graph
     */
    @Operation(summary = "Update existing job", description = "Update existing job with a new structure")
    @PostMapping("{projectId}/job/{id}")
    public void update(
        @PathVariable String projectId, @PathVariable String id, @Valid @RequestBody JobRequestDto jobRequestDto) {
        LOGGER.info(
            "{} - Updating job '{}' in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        jobService.update(id, projectId, jobRequestDto);
        LOGGER.info(
            "{} - Job '{}' in project '{}' successfully updated",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
    }

    /**
     * Getting job in project by id.
     *
     * @param projectId project id
     * @param id        job id
     * @return ResponseEntity with job graph
     */
    @Operation(summary = "Get information about the job", description = "Fetch job's structure by id")
    @GetMapping("{projectId}/job/{id}")
    public JobResponseDto get(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info(
            "{} - Receiving job '{}' in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        return jobService.get(projectId, id);
    }

    /**
     * Deleting job in project by id.
     *
     * @param projectId project id
     * @param id        job id
     */
    @Operation(summary = "Delete the job", description = "Delete existing job with all it's instances",
        responses = {
        @ApiResponse(responseCode = "204", description = "Indicates successful job deletion")})
    @DeleteMapping("{projectId}/job/{id}")
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info(
            "{} - Deleting '{}' job in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        jobService.delete(projectId, id);
        LOGGER.info(
            "{} - Job '{}' in project '{}' successfully deleted",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Getting job logs.
     *
     * @param projectId project id
     * @param id        job id
     * @return ResponseEntity with list of logs objects
     */
    @Operation(summary = "Get job logs", description = "Get all logs for a specific job")
    @GetMapping("{projectId}/job/{id}/logs")
    public List<LogDto> getLogs(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info(
            "{} - Receiving job '{}' logs in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        return jobService.getJobLogs(projectId, id);
    }

    /**
     * Run job.
     *
     * @param projectId project id
     * @param id        job id
     */
    @Operation(summary = "Run the job", description = "Create a new pod with configuration to execute a spark-job")
    @PostMapping("{projectId}/job/{id}/run")
    public void run(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info(
            "{} - Running job '{}' in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        jobService.run(projectId, id);
        LOGGER.info(
            "{} - Job '{}' in project '{}' successfully started",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
    }

    /**
     * Stop job.
     *
     * @param projectId project id
     * @param id        job id
     */
    @Operation(summary = "Stop the job", description = "Stop/delete the pod with a running spark-job")
    @PostMapping("{projectId}/job/{id}/stop")
    public void stop(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info(
            "{} - Stopping job '{}' in project '{}'",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
        jobService.stop(projectId, id);
        LOGGER.info(
            "{} - Job '{}' in project '{}' successfully stopped",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
            id,
            projectId);
    }
}
