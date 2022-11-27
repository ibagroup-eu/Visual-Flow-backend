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


import by.iba.vfapi.dao.LogRepository;
import by.iba.vfapi.dao.LogRepositoryImpl;
import by.iba.vfapi.dto.LogDto;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import static by.iba.vfapi.dto.Constants.LOGS;
import static by.iba.vfapi.dto.Constants.CONTAINER_NODE_ID;
import static by.iba.vfapi.dto.Constants.PIPELINE_ID_LABEL;
import static by.iba.vfapi.services.KubernetesService.NO_POD_MESSAGE;

/**
 * Log service class.
 */
@Slf4j
@Service
public class LogService {
    private final LogRepository logRepository;
    private final KubernetesService kubernetesService;

    public LogService(LogRepositoryImpl logRepository, KubernetesService kubernetesService) {
        this.logRepository = logRepository;
        this.kubernetesService = kubernetesService;
    }

    /**
     * Getting history logs.
     *
     * @param projectId projectId
     * @param id        pod id
     * @param logId     logId
     * @return logs as a String
     */
    public String getHistoryLogsByKey(final String projectId, final String id, final String logId) {
        return logRepository.get(String.format("%s:%s_%s_%s", LOGS, projectId, id, logId));
    }

    /**
     * Retrieve custom container logs
     *
     * @param projectId  project id
     * @param pipelineId pipeline id
     * @param nodeId     node id
     * @return list of log entries
     */
    public List<LogDto> getCustomContainerLogs(String projectId, String pipelineId, String nodeId) {
        return getParsedLogs(() -> kubernetesService.getPodLogs(
                projectId,
                kubernetesService.getPodsByLabels(projectId,
                                                  Map.of(CONTAINER_NODE_ID, nodeId,
                                                         PIPELINE_ID_LABEL, pipelineId)
                        )
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Cannot find any pods by provided labels")
                        )
                        .getMetadata()
                        .getName()));
    }

    /**
     * Getting job logs.
     *
     * @param projectId project id
     * @param id        job id
     * @return list of log objects
     */
    public List<LogDto> getParsedPodLogs(final String projectId, final String id) {
        return getParsedLogs(() -> kubernetesService.getPodLogs(projectId, id));
    }

    /**
     * Getting job logs from history.
     *
     * @param projectId project id
     * @param id        job id
     * @return list of log objects
     */
    public List<LogDto> getParsedHistoryLogs(final String projectId, final String id, final String logId) {
        return getParsedLogs(() -> getHistoryLogsByKey(projectId, id, logId));
    }

    /**
     * Parses provided logs
     *
     * @param logSupplier log supplier
     * @return list of parsed log objects
     */
    public List<LogDto> getParsedLogs(Supplier<String> logSupplier) {
        try {
            String logs = Objects.toString(logSupplier.get(), "");
            String[] logItems = logs.split("\n");
            List<LogDto> logResults = checkLogItems(logItems);

            if (logResults.isEmpty()) {
                logResults.add(LogDto.builder().message(logs).build());
            }

            return logResults;
        } catch (ResourceNotFoundException e) {
            LOGGER.info(NO_POD_MESSAGE, e);
            return Collections.emptyList();
        }
    }

    /**
     * Secondary method for creating logResults list from log items.
     *
     * @param logItems is a list of log's items.
     * @return log's results.
     */
    private static List<LogDto> checkLogItems(String[] logItems) {
        List<LogDto> logResults = new ArrayList<>();
        int logIndex = 0;
        for (String logItem : logItems) {
            Matcher matcher = K8sUtils.LOG_PATTERN.matcher(logItem);
            if (matcher.matches()) {
                logResults.add(LogDto.fromMatcher(matcher));
                logIndex++;
            } else {
                if (logIndex != 0) {
                    LogDto lastLog = logResults.get(logIndex - 1);
                    logResults.set(logIndex - 1, lastLog.withMessage(lastLog.getMessage() + "\n" + logItem));
                }
            }
        }
        return logResults;
    }
}
