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

import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dao.LogRepositoryImpl;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.model.history.JobHistory;
import by.iba.vfapi.services.utils.K8sUtils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;

/**
 * PodWatcher class.
 */
@Slf4j
public class PodWatcher implements Watcher<Pod> {
    private final JobHistoryRepository historyRepository;
    private final LogRepositoryImpl logRepository;
    private final NamespacedKubernetesClient client;
    private final CountDownLatch latch;

    /**
     * Constructor for class PodWatcher.
     *
     * @param historyRepository repository
     * @param latch latch
     */
    public PodWatcher(final JobHistoryRepository historyRepository,
                      final LogRepositoryImpl logRepository,
                      final CountDownLatch latch,
                      NamespacedKubernetesClient client) {
        this.historyRepository = historyRepository;
        this.logRepository = logRepository;
        this.latch = latch;
        this.client = client;
    }

    /**
     * Receives event.
     *
     * @param action event's action
     * @param pod pod
     */
    @Override
    public void eventReceived(Action action, Pod pod) {
        if (pod.getStatus() != null && (pod.getStatus().getPhase().equals(K8sUtils.FAILED_STATUS) ||
            pod.getStatus().getPhase().equals(K8sUtils.SUCCEEDED_STATUS))) {
            String timeStampMillis = String.valueOf(Instant.now().toEpochMilli());
            String key = pod.getMetadata().getNamespace() + "_" + pod.getMetadata().getName();
            if (!isInteractiveMode(pod)) {
                JobHistory history = new JobHistory(
                        pod.getMetadata().getName(),
                        pod.getMetadata().getLabels().get(Constants.TYPE),
                        pod.getStatus().getStartTime(),
                        K8sUtils.extractTerminatedStateField(pod.getStatus(), ContainerStateTerminated::getFinishedAt),
                        pod.getMetadata().getLabels().get(Constants.STARTED_BY),
                        pod.getStatus().getPhase());
                historyRepository.add(key, timeStampMillis, history);
                LOGGER.info("Job's history successfully saved: {}", history);
            } else {
                LOGGER.info("Job is running in interactive mode with uid {}, skipping history",
                        pod.getMetadata().getUid());
            }
            saveLogs(key, timeStampMillis, pod);
            latch.countDown();
        }
    }

    /**
     * Checks if the pod has an environment variable VISUAL_FLOW_RUNTIME_MODE with value INTERACTIVE.
     *
     * @param pod the pod to check
     * @return true if the environment variable is found with the specified value, false otherwise
     */
    private static boolean isInteractiveMode(Pod pod) {
        for (Container container : pod.getSpec().getContainers()) {
            for (EnvVar envVar : container.getEnv()) {
                if (Constants.VISUAL_FLOW_RUNTIME_MODE.equals(envVar.getName())
                        && Constants.INTERACTIVE.equals(envVar.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Save pod logs.
     *
     * @param key exception
     * @param pod pod
     */
    private void saveLogs(String key, String timeStampMillis, Pod pod) {
        String logKey = "logs:" + key + "_" + timeStampMillis;
        logRepository.set(logKey, client.pods()
                .inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName())
                .getLog());
        LOGGER.info("Pod logs have been successfully saved: {}", key);
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
