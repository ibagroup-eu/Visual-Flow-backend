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
import by.iba.vfapi.services.DatabasesService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;

/**
 * Controller for manipulations with DB-service.
 */
@Slf4j
@Tag(name = "Databases API", description = "Manage DB connections")
@RestController
@RequestMapping("api/db")
public class DatabasesController {

    private final DatabasesService databaseService;
    private final AuthenticationService authenticationService;
    private final RestTemplate restTemplate;
    private final String dbServiceHost;

    /**
     * Initialization constructor.
     * @param databaseService is needed to get connections objects.
     * @param authenticationService is needed to auth user before execution operations.
     * @param restTemplate is needed to make HTTP requests.
     * @param dbServiceHost is DB-service host, declared in yaml.
     */
    public DatabasesController(DatabasesService databaseService,
                               AuthenticationService authenticationService,
                               RestTemplate restTemplate,
                               @Value("${db-service.host}") String dbServiceHost) {
        this.databaseService = databaseService;
        this.authenticationService = authenticationService;
        this.restTemplate = restTemplate;
        this.dbServiceHost = dbServiceHost;
    }

    /**
     * Method for getting connection ping status by project id and connection name.
     * @param projectId is project id.
     * @param name is connection name.
     * @return true - if connection has been established successfully, otherwise - false.
     */
    @Operation(summary = "Ping connection by its name",
            description = "Get a connection ping status")
    @GetMapping("/{projectId}/connections/{name}")
    public ResponseEntity<PingStatusDto> ping(@PathVariable final String projectId, @PathVariable final String name) {
        LOGGER.info(
                "{} - Receiving {} connection ping status for the '{}' project",
                AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
                name,
                projectId);
        ConnectDto connection = databaseService.getConnection(projectId, name);
        if (connection == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"The connection has not been found!");
        } else {
            return restTemplate.postForEntity(dbServiceHost, connection, PingStatusDto.class);
        }
    }

    /**
     * Method for getting connection ping status with provided parameters.
     * @param projectId is project id.
     * @param connectionDto is JSON containing user parameters.
     * @return true - if connection has been established successfully, otherwise - false.
     */
    @Operation(summary = "Ping connection with certain parameters",
            description = "Get a connection ping status")
    @PostMapping("/{projectId}/connections")
    public ResponseEntity<PingStatusDto> ping(@PathVariable final String projectId,
                                                        @RequestBody @Valid final ConnectDto connectionDto) {
        LOGGER.info(
                "{} - Receiving {} connection ping status for the '{}' project",
                AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
                connectionDto.getKey(),
                projectId);
        return restTemplate.postForEntity(dbServiceHost, connectionDto, PingStatusDto.class);
    }

}
