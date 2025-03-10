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

import by.iba.vfapi.services.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@Tag(name = "Public API", description = "Public API")
@RequiredArgsConstructor
@RestController
@RequestMapping("public/api")
public class PublicApiController {
    private final JobService jobService;

    /**
     * Updating job params in project by id.
     *
     * @param projectId     project id
     * @param id            job id
     * @param jobParams object with name and graph
     */
    @Operation(summary = "Update existing job definition", description = "Update existing job definition")
    @PatchMapping("/project/{projectId}/job/{id}/definition")
    public void patchParams(
            @PathVariable String projectId, @PathVariable String id, @Valid @RequestBody JsonNode jobParams) {
        LOGGER.info(
                "Anonymous - Updating job params '{}' in project '{}'",
                id,
                projectId);
        jobService.updateDefinition(id, projectId, jobParams);
        LOGGER.info(
                "Anonymous - Job params '{}' in project '{}' successfully updated",
                id,
                projectId);
    }
}
