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

import by.iba.vfapi.dao.PipelineHistoryRepository;
import by.iba.vfapi.model.history.AbstractHistory;
import io.fabric8.kubernetes.client.Watch;
import lombok.SneakyThrows;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;

/**
 * WorkflowService class.
 */
@Service
public class WorkflowService {
    private final ArgoKubernetesService argoKubernetesService;
    private final PipelineHistoryRepository<? extends AbstractHistory> historyRepository;

    /**
     * Constructor for class WorkflowService.
     *
     * @param argoKubernetesService argoKubernetesService
     */
    public WorkflowService(
        final ArgoKubernetesService argoKubernetesService,
        final PipelineHistoryRepository<? extends AbstractHistory> historyRepository) {
        this.argoKubernetesService = argoKubernetesService;
        this.historyRepository = historyRepository;
    }

    /**
     * Asynchronously launches tracking of workflow's events.
     *
     * @param projectId  project id
     * @param pipelineId pipeline id
     */
    @Async
    @SneakyThrows
    public void trackWorkflowEvents(final String projectId, final String pipelineId) {
        CountDownLatch latch = new CountDownLatch(1);
        Watch watch = argoKubernetesService.watchWorkflow(projectId, pipelineId, historyRepository, latch);
        latch.await();
        watch.close();
    }
}
