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

package by.iba.vfapi.services.watchers;

import by.iba.vfapi.dao.LogRepositoryImpl;
import by.iba.vfapi.dao.PipelineHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.model.argo.WorkflowStatus;
import by.iba.vfapi.model.history.AbstractHistory;
import by.iba.vfapi.model.history.PipelineHistory;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.history.PipelineNodeHistory;
import by.iba.vfapi.services.K8sUtils;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.PipelineService;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * WorkflowWatcher class.
 */
@Slf4j
public class WorkflowWatcher implements Watcher<Workflow> {
    private final PipelineHistoryRepository<? extends AbstractHistory> historyRepository;
    private final CountDownLatch latch;
    private final KubernetesService kubernetesService;
    private final LogRepositoryImpl logRepository;

    /**
     * Constructor for class WorkflowWatcher.
     *
     * @param historyRepository repository
     * @param latch latch
     */
    public WorkflowWatcher(final PipelineHistoryRepository<? extends AbstractHistory> historyRepository,
                           final LogRepositoryImpl logRepository,
                           final CountDownLatch latch,
                           final KubernetesService kubernetesService) {
        this.historyRepository = historyRepository;
        this.logRepository = logRepository;
        this.latch = latch;
        this.kubernetesService = kubernetesService;
    }

    /**
     * Receives event.
     *
     * @param action event's action
     * @param workflow workflow
     */
    @Override
    public void eventReceived(Action action, Workflow workflow) {
        WorkflowStatus workflowStatus = workflow.getStatus();
        if (workflowStatus != null && List.of(K8sUtils.FAILED_STATUS,
                                              K8sUtils.SUCCEEDED_STATUS).contains(workflowStatus.getPhase())) {

            List<String> validTemplates = List.of(
                    PipelineService.SPARK_TEMPLATE_NAME, PipelineService.PIPELINE_TEMPLATE_NAME,
                    PipelineService.NOTIFICATION_TEMPLATE_NAME, PipelineService.CONTAINER_TEMPLATE_NAME,
                    PipelineService.CONTAINER_WITH_CMD_TEMPLATE_NAME,
                    PipelineService.CONTAINER_TEMPLATE_WITH_PROJECT_PARAMS_NAME,
                    PipelineService.CONTAINER_WITH_CMD_AND_PROJECT_PARAMS_TEMPLATE_NAME,
                    Constants.EMAIL_NOTIFY_SUCCESS, Constants.EMAIL_NOTIFY_FAILURE,
                    Constants.SLACK_NOTIFY_SUCCESS, Constants.SLACK_NOTIFY_FAILURE);

            List<String> workflowNodes = workflowStatus
                    .getNodes()
                    .entrySet()
                    .stream()
                    .filter(nodes -> !nodes.getKey().equals(workflow.getMetadata().getName()))
                    .filter(nodes -> List.of(K8sUtils.FAILED_STATUS,
                                             K8sUtils.SUCCEEDED_STATUS,
                                             K8sUtils.ERROR_STATUS).contains(nodes.getValue().getPhase()))
                    .filter(nodes -> nodes.getValue().getTemplateName() != null &&
                                     validTemplates.contains(nodes.getValue().getTemplateName())
                    ).map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            String hashKey = String.valueOf(workflow.getStatus().getStartedAt().getMillis());
            String key = String.format("%s:%s_%s",
                    Constants.PIPELINE_HISTORY,
                    workflow.getMetadata().getNamespace(),
                    workflow.getMetadata().getName());

            if(!isHistoryExist(key, hashKey)) {
                savePipelineHistory(workflow, workflowNodes, key, hashKey);
                savePipelineNodeHistory(workflow, workflowNodes, hashKey);
            }
            latch.countDown();
        }
    }

    /**
     * Used to save pipeline's history.
     *
     * @param workflow workflow
     * @param workflowNodes pipelines' nodes
     */
    private void savePipelineHistory(Workflow workflow, List<String> workflowNodes, String key, String hashKey) {
        PipelineHistory pipelineHistory = new PipelineHistory(
            workflow.getMetadata().getName(),
            workflow.getMetadata().getLabels().get(Constants.TYPE),
            workflow.getStatus().getStartedAt().toString(),
            workflow.getStatus().getFinishedAt().toString(),
            workflow.getMetadata().getLabels().get(Constants.STARTED_BY),
            workflow.getStatus().getPhase(),
            workflowNodes);

        historyRepository.add(key, hashKey, pipelineHistory);
        LOGGER.info("Pipeline's history successfully saved: {}", pipelineHistory);
    }

    /**
     * Used to save pipeline node's history.
     *
     * @param workflow workflow
     * @param workflowNodes pipelines' nodes
     */
    private void savePipelineNodeHistory(Workflow workflow, List<String> workflowNodes, String hashKey) {
        workflowNodes.forEach((String node) -> {
            String projectId = workflow.getMetadata().getNamespace();
            Map<String, String> nodeLabels = kubernetesService.getPodLabels(projectId, node);
            PipelineNodeHistory pipelineNodeHistory = new PipelineNodeHistory(
                node,
                nodeLabels.get(Constants.NODE_NAME),
                nodeLabels.get(Constants.NODE_OPERATION),
                workflow.getStatus().getNodes().get(node).getStartedAt().toString(),
                workflow.getStatus().getNodes().get(node).getFinishedAt().toString(),
                workflow.getStatus().getNodes().get(node).getPhase()
            );

            String nodeKey = String.format("%s:%s_%s_%s",
                    Constants.PIPELINE_NODE_HISTORY,
                    projectId,
                    node,
                    hashKey);
            String nodeHashKey = String.valueOf(Instant.now().toEpochMilli());

            historyRepository.add(nodeKey, nodeHashKey, pipelineNodeHistory);
            LOGGER.info("Pipeline node's history successfully saved: {}", pipelineNodeHistory);

            saveLogs(nodeHashKey, projectId, node);
        });
    }

    /**
     * Save the logs for pod.
     *
     * @param logId     log id
     * @param projectId projectId
     * @param id        pod id
     */
    private void saveLogs(String logId, String projectId, String id) {
        String key = String.format("%s:%s_%s_%s", Constants.LOGS, projectId, id, logId);
        logRepository.set(key, kubernetesService.getPodLogs(projectId, id));
        LOGGER.info("Pod logs have been successfully saved: {}", key);
    }

    /**
     * Check if the history is already exists
     *
     * @param key     key
     * @param hashKey hash key
     * @return boolean true/false
     */
    private boolean isHistoryExist(String key, String hashKey) {
        return historyRepository.hasKey(key, hashKey);
    }

    /**
     * Receives exception.
     *
     * @param e exception
     */
    @Override
    public void onClose(WatcherException e) {
        if (e != null) {
            LOGGER.error("Watch error received: {}", e.getMessage(), e);
            latch.countDown();
        }
    }
}
