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
import by.iba.vfapi.services.LogService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;


/**
 * Log controller class.
 */
@Slf4j
@Tag(name = "Log API", description = "Retrieve logs")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
public class LogController {
    private final AuthenticationService authenticationService;
    private  final LogService logService;

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
        return logService.getParsedPodLogs(projectId, id);
    }

    /**
     * Getting custom container logs.
     *
     * @param projectId  project id
     * @param pipelineId pipeline id
     * @param nodeId     node id
     * @return ResponseEntity with list of logs objects
     */
    @Operation(summary = "Get custom container logs", description = "Get all logs for a specific custom container")
    @GetMapping("{projectId}/pipeline/{pipelineId}/{nodeId}/logs")
    public List<LogDto> getCustomContainerLogs(
            @PathVariable String projectId, @PathVariable String pipelineId, @PathVariable String nodeId) {
        LOGGER.info(
                "{} - Receiving custom container in project '{}', pipeline '{}' at node '{}'",
                AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
                projectId,
                pipelineId,
                nodeId);
        return logService.getCustomContainerLogs(projectId, pipelineId, nodeId);
    }

    /**
     * Getting logs by history.
     *
     * @param projectId project id
     * @param id        pod id
     * @param logId     logId
     * @return ResponseEntity with list of logs objects
     */
    @Operation(summary = "Get job/pipeline logs from storage", description = "Get job/pipeline logs from storage")
    @GetMapping("{projectId}/job/{id}/logsHistory/{logId}")
    public List<LogDto> getLogsHistory(
            @PathVariable String projectId,
            @PathVariable String id,
            @PathVariable String logId) {
        LOGGER.info(
                "{} - Receiving job/pipeline logs with id '{}' in project '{}'",
                AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
                id,
                projectId
        );
        return logService.getParsedHistoryLogs(projectId, id, logId);
    }

}
