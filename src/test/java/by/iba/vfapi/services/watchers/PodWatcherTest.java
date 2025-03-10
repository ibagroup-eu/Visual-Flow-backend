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
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PodWatcherTest {
    @Mock
    private JobHistoryRepository historyRepository;
    @Mock
    private LogRepositoryImpl logRepository;
    @Mock
    private CountDownLatch latch;
    private PodWatcher podWatcher;
    private final KubernetesServer server = new KubernetesServer();

    @BeforeEach
    void setUp() {
        server.before();
        podWatcher = new PodWatcher(historyRepository, logRepository, latch, server.getClient());
    }

    @AfterEach
    void tearDown() {
        server.after();
    }

    @Test
    void testEventReceivedWithInteractiveMode() {
        PodStatus podStatus = new PodStatus();
        podStatus.setPhase(K8sUtils.SUCCEEDED_STATUS);
        podStatus.setStartTime("test");
        ContainerState containerState = new ContainerState();
        containerState.setTerminated(new ContainerStateTerminated());
        ContainerStatus containerStatus = new ContainerStatus();
        containerStatus.setState(containerState);
        podStatus.setContainerStatuses(List.of(containerStatus));
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withNamespace("vf")
                .withName("pod1")
                .addToLabels(Constants.TYPE, "job")
                .addToLabels(Constants.STARTED_BY, "test_user")
                .withUid("12345")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .addNewEnv()
                .withName(Constants.VISUAL_FLOW_RUNTIME_MODE)
                .withValue(Constants.INTERACTIVE)
                .endEnv()
                .endContainer()
                .endSpec()
                .withStatus(podStatus)
                .build();

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/vf/pods/pod1/log?pretty=false")
                .andReturn(HttpURLConnection.HTTP_OK, "log")
                .once();

        podWatcher.eventReceived(Watcher.Action.MODIFIED, pod);
        verify(historyRepository, never()).add(anyString(), anyString(), any(JobHistory.class));
        verify(latch).countDown();
    }

    @Test
    void testEventReceived() {
        PodStatus podStatus = new PodStatus();
        podStatus.setPhase(K8sUtils.SUCCEEDED_STATUS);
        podStatus.setStartTime("test");
        ContainerState containerState = new ContainerState();
        containerState.setTerminated(new ContainerStateTerminated());
        ContainerStatus containerStatus = new ContainerStatus();
        containerStatus.setState(containerState);
        podStatus.setContainerStatuses(List.of(containerStatus));
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withNamespace("vf")
                .withName("pod1")
                .addToLabels(Constants.TYPE, "job")
                .addToLabels(Constants.STARTED_BY, "test_user")
                .withResourceVersion("1")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .endContainer()
                .endSpec()
                .withStatus(podStatus)
                .build();

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/vf/pods/pod1/log?pretty=false")
                .andReturn(HttpURLConnection.HTTP_OK, "log")
                .once();

        podWatcher.eventReceived(Watcher.Action.MODIFIED, pod);
        verify(historyRepository).add(eq("vf_pod1"), anyString(), any(JobHistory.class));
        verify(latch).countDown();
    }

    @Test
    void testOnClose() {
        WatcherException e = null;
        podWatcher.onClose(e);
        verify(latch, never()).countDown();

        e = new WatcherException("test");
        podWatcher.onClose(e);
        verify(latch).countDown();
    }
}
