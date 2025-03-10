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

import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.ListWithOffset;
import by.iba.vfapi.services.JobSessionService;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Optional;

/**
 * Interactive session controller class.
 */
@Slf4j
@Tag(name = "Job interactive session API")
@RequiredArgsConstructor
@RestController
public class InteractiveSessionController {
    private static final String BASE_PATH = "/api/project/{projectId}/job/{jobId}/session/{runId}";
    private static final String PUBLIC_PREFIX = "/public";
    private final JobSessionService jobService;
    private final AuthenticationService authenticationService;

    /**
     * Update interactive session definition
     *
     * @param projectId project id
     * @param jobId     job id
     * @param runId     pod uuid
     * @param payload   object with name and graph
     * @return
     */
    @Operation(summary = "Update interactive job session")
    @PutMapping(BASE_PATH)
    public ResponseEntity<String> updateSession(@PathVariable String projectId,
                                                @PathVariable String jobId,
                                                @PathVariable String runId,
                                                @Valid @RequestBody JsonNode payload) {
        LOGGER.info(
                "{} - Updating session '{}' in project '{}' pod '{}",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        Optional<String> body = jobService.updateSession(runId, payload);
        LOGGER.info(
                "{} - Session '{}' in project '{}' pod '{}' successfully updated",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        return ResponseEntity.of(body);
    }

    /**
     * GET original job definition in project by id.
     *
     * @param projectId project id
     * @param jobId     job id
     * @param runId     pod uuid
     * @return job definition
     */
    @Operation(summary = "Get interactive job session")
    @GetMapping(BASE_PATH)
    public ResponseEntity<JsonNode> getSession(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String runId) {
        LOGGER.info(
                "{} - Fetching job session '{}' in project '{}' pod uuid '{}'",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        Optional<JsonNode> definition = jobService.findSession(runId);
        return ResponseEntity.of(definition);
    }

    /**
     * GET transformed job definition in project by id.
     *
     * @param projectId project id
     * @param jobId     job id
     * @param runId     pod uuid
     * @return job definition
     */
    @Operation(summary = "Get interactive job definition")
    @GetMapping( PUBLIC_PREFIX + BASE_PATH)
    public ResponseEntity<GraphDto> getTransformedSession(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String runId) {
        LOGGER.info(
                "{} - Fetching transformed job definition '{}' in project '{}' pod uuid '{}'",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        Optional<GraphDto> definition = jobService.findGraphDto(runId);
        return ResponseEntity.of(definition);
    }

    @Operation(summary = "Remove interactive job session")
    @DeleteMapping(BASE_PATH)
    ResponseEntity<Void> deleteSession(@PathVariable String projectId,
                                       @PathVariable String jobId,
                                       @PathVariable String runId) {
        LOGGER.info(
                "{} - Removing job session '{}' in project '{}' pod uuid '{}'",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        jobService.removeSession(runId);
        return ResponseEntity.noContent().build();
    }

    /**
     * create job event in session.
     *
     * @param projectId project id
     * @param jobId     job id
     * @param runId     pod uuid
     * @param payload   object with name and graph
     * @return
     */
    @Operation(summary = "Post interactive session event")
    @PostMapping(BASE_PATH + "/events")
    public ResponseEntity<Long> addEvent(@PathVariable String projectId,
                                         @PathVariable String jobId,
                                         @PathVariable String runId,
                                         @Valid @RequestBody JsonNode payload) {
        LOGGER.info(
                "{} - Adding event '{}' in project '{}' pod '{}",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        Optional<Long> result = jobService.createEvent(runId, payload);
        LOGGER.info(
                "{} - Event '{}' in project '{}' pod '{}' successfully added",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        return ResponseEntity.of(result);
    }

    /**
     * Get interactive job events
     *
     * @param projectId project id
     * @param jobId     job id
     * @param runId     pod uuid
     * @return job events
     */
    @Operation(summary = "Get interactive job events")
    @GetMapping(PUBLIC_PREFIX + BASE_PATH + "/events")
    public ResponseEntity<ListWithOffset<JsonNode>> getEvents(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String runId,
            @RequestParam(required = false, defaultValue = "0") Long offset) {

        LOGGER.info(
                "{} - Fetching job events '{}' in project '{}' pod uuid '{}'",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        ListWithOffset<JsonNode> events = jobService.findEvents(runId, offset);
        return ResponseEntity.ok(events);
    }

    /**
     * Get job metadata in project by id.
     *
     * @param projectId project id
     * @param jobId     job id
     * @return ResponseEntity with job graph
     */
    @Operation(summary = "Get interactive job metadata")
    @GetMapping(BASE_PATH + "/metadata")
    public ResponseEntity<ListWithOffset<JsonNode>> getMetadata(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable String runId,
            @RequestParam(required = false, defaultValue = "0") Long offset) {
        LOGGER.info(
                "{} - Receiving job metadata '{}' in project '{}' pod '{}'",
                authenticationService.getFormattedUserInfo(),
                jobId,
                projectId,
                runId);
        ListWithOffset<JsonNode> body = jobService.getMetadata(runId, offset);
        return ResponseEntity.ok(body);
    }

    /**
     * Update job metadata in project by id.
     *
     * @param projectId project id
     * @param jobId     job id
     * @param runId     pod uuid
     * @param payload   json metadata
     * @return identifier
     */
    @Operation(summary = "Post interactive job metadata")
    @PostMapping(PUBLIC_PREFIX + BASE_PATH + "/metadata")
    public ResponseEntity<Long> updateMetadata(@PathVariable String projectId,
                                               @PathVariable String jobId,
                                               @PathVariable String runId,
                                               @Valid @RequestBody JsonNode payload) {
        LOGGER.info(
                "Anonymous - Receiving job '{}' in project '{}' pod '{}'",
                jobId,
                projectId,
                runId);
        Optional<Long> metadata = jobService.createMetadata(runId, payload);
        LOGGER.info(
                "Anonymous - Metadata '{}' in project '{}' pod '{}' successfully updated",
                jobId,
                projectId,
                runId);
        return ResponseEntity.of(metadata);
    }
}
