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
import by.iba.vfapi.model.argo.NodeStatus;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.argo.WorkflowStatus;
import by.iba.vfapi.services.K8sUtils;
import by.iba.vfapi.services.KubernetesService;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.WatcherException;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.fabric8.kubernetes.client.Watcher;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WorkflowWatcherTest {
    @Mock
    private PipelineHistoryRepository historyRepository;
    @Mock
    private LogRepositoryImpl logRepository;
    @Mock
    private KubernetesService kubernetesService;
    @Mock
    private CountDownLatch latch;
    private WorkflowWatcher workflowWatcher;

    @BeforeEach
    void setUp() {
        workflowWatcher = new WorkflowWatcher(historyRepository, logRepository, latch, kubernetesService);
    }

    @Test
    void testEventReceived() {
        Workflow workflow = new Workflow();
        workflow.setStatus(null);
        Watcher.Action action = Watcher.Action.MODIFIED;
        workflowWatcher.eventReceived(action, workflow);
        verify(latch, never()).countDown();

        NodeStatus nodeStatus = new NodeStatus();
        nodeStatus.setStartedAt(DateTime.now());
        nodeStatus.setFinishedAt(DateTime.now());
        nodeStatus.setPhase(K8sUtils.SUCCEEDED_STATUS);

        WorkflowStatus workflowStatus = new WorkflowStatus();
        workflowStatus.setPhase(K8sUtils.SUCCEEDED_STATUS);
        workflowStatus.setStartedAt(DateTime.now());
        workflowStatus.setFinishedAt(DateTime.now());
        workflowStatus.setNodes(Map.of("nodeId", nodeStatus));

        workflow.setMetadata(new ObjectMetaBuilder()
            .withNamespace("vf")
            .withName("wf")
            .addToLabels(Constants.TYPE, "job")
            .addToLabels(Constants.STARTED_BY, "test_user")
            .withResourceVersion("1")
            .build());
        workflow.setStatus(workflowStatus);
        workflowWatcher.eventReceived(action, workflow);
        verify(latch).countDown();
    }

    @Test
    void testOnClose() {
        WatcherException e = null;
        workflowWatcher.onClose(e);
        verify(latch, never()).countDown();

        e = new WatcherException("test");
        workflowWatcher.onClose(e);
        verify(latch).countDown();
    }
}
