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

package by.iba.vfapi.services;

import by.iba.vfapi.config.ApplicationConfigurationProperties;
import by.iba.vfapi.dao.JobDefinitionRepository;
import by.iba.vfapi.dao.JobEventRepository;
import by.iba.vfapi.dao.JobMetadataRepository;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.ListWithOffset;
import by.iba.vfapi.services.utils.GraphUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JobSessionService {
    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobEventRepository jobEventRepository;
    private final JobMetadataRepository jobMetadataRepository;
    private final ApplicationConfigurationProperties applicationConfigurationProperties;

    public Optional<Long> createEvent(String runId, JsonNode payload) {
        return this.findByKeyAndUpdateExpiry(runId)
                .map(x -> jobEventRepository.save(runId, payload));
    }

    public JsonNode createSession(String runId, JsonNode payload) {
        jobDefinitionRepository.save(runId, payload);
        return findByKeyAndUpdateExpiry(runId).orElseThrow();
    }

    public Optional<String> updateSession(String runId, JsonNode payload) {
        return this.findByKeyAndUpdateExpiry(runId)
                .map(x -> jobDefinitionRepository.save(runId, payload));
    }

    public Optional<JsonNode> findSession(String runId) {
        return jobDefinitionRepository.findByKey(runId);
    }

    public ListWithOffset<JsonNode> findEvents(String runId, Long offset) {
        if (offset == null) {
            offset = 0L;
        }
        return jobEventRepository.findAll(runId, offset);
    }

    public ListWithOffset<JsonNode> getMetadata(String runId, Long offset) {
        if (offset == null) {
            offset = 0L;
        }
        return jobMetadataRepository.findAll(runId, offset);
    }

    public Optional<Long> createMetadata(String runId, JsonNode payload) {
        return findSession(runId)
                .map(x -> jobMetadataRepository.save(runId, payload));
    }

    public Optional<JsonNode> findByKeyAndUpdateExpiry(String runId) {
        long timeout = getTimeout();
        return jobDefinitionRepository.findByKeyAndUpdateExpiry(runId, timeout);
    }

    private long getTimeout() {
        return Optional.ofNullable(applicationConfigurationProperties.getJob())
                .map(ApplicationConfigurationProperties.JobSettings::getInteractiveSessionTimeout)
                .orElse(-1L);
    }

    @Transactional
    public void removeSession(String runId) {
        jobDefinitionRepository.delete(runId);
        jobEventRepository.delete(runId);
        jobMetadataRepository.delete(runId);
    }

    public Optional<GraphDto> findGraphDto(String runId) {
        return findSession(runId)
                .map(GraphUtils::parseGraph);
    }
}
