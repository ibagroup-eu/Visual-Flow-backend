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

import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dao.LogRepositoryImpl;
import io.fabric8.kubernetes.client.Watch;
import lombok.SneakyThrows;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;

/**
 * PodService class.
 */
@Service
public class PodService {
    private final KubernetesService kubernetesService;
    private final JobHistoryRepository historyRepository;
    private final LogRepositoryImpl logRepository;

    /**
     * Constructor for class PodService.
     *
     * @param kubernetesService kubernetesService
     */
    public PodService(
            final KubernetesService kubernetesService,
            final JobHistoryRepository historyRepository,
            final LogRepositoryImpl logRepository) {
        this.kubernetesService = kubernetesService;
        this.historyRepository = historyRepository;
        this.logRepository = logRepository;
    }

    /**
     * Asynchronously launches tracking of pod's events.
     *
     * @param projectId project id
     * @param jobId     job id
     */
    @Async
    @SneakyThrows
    public void trackPodEvents(final String projectId, final String jobId) {
        if(historyRepository.recordLogs()){
            CountDownLatch latch = new CountDownLatch(1);
            Watch watch = kubernetesService.watchPod(projectId, jobId, historyRepository, logRepository,latch);
            latch.await();
            watch.close();
        }
    }
}
