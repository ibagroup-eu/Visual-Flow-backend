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

import by.iba.vfapi.dao.LogRepositoryImpl;
import by.iba.vfapi.dao.PipelineHistoryRepository;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.model.history.AbstractHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.api.model.WatchEventBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.HttpURLConnection;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {
    private final KubernetesServer server = new KubernetesServer();
    private static final String APP_NAME = "vf";
    private static final String APP_NAME_LABEL = "testApp";
    private static final String PVC_MOUNT_PATH = "/files";
    private static final String IMAGE_PULL_SECRET = "vf-dev-image-pull";
    private static final Long EVENT_WAIT_PERIOD_MS = 10L;

    @Mock
    private AuthenticationService authenticationServiceMock;
    @Mock
    private ArgoKubernetesService argoKubernetesService;
    @Mock
    private PipelineHistoryRepository<? extends AbstractHistory> historyRepository;
    @Mock
    private LogRepositoryImpl logRepository;
    private WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        server.before();
        this.argoKubernetesService = new ArgoKubernetesService(
            server.getClient(),
            APP_NAME,
            APP_NAME_LABEL,
            PVC_MOUNT_PATH,
            IMAGE_PULL_SECRET,
            authenticationServiceMock,
            logRepository
        );
        this.workflowService = new WorkflowService(argoKubernetesService, historyRepository);
    }

    @AfterEach
    void tearDown() {
        server.after();
    }

    @Test
    void testTrackWorkflowEvents() {
        mockAuthenticationService();

        server.expect()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflows?fieldSelector=metadata.name%3Dwf&watch=true")
            .andUpgradeToWebSocket()
            .open()
            .waitFor(EVENT_WAIT_PERIOD_MS)
            .andEmit(outdatedEvent())
            .done()
            .once();

        workflowService.trackWorkflowEvents("vf", "wf");
    }

    private void mockAuthenticationService() {
        UserInfo ui = new UserInfo();
        ui.setSuperuser(true);
        when(authenticationServiceMock.getUserInfo()).thenReturn(ui);
    }

    private static WatchEvent outdatedEvent() {
        return new WatchEventBuilder().withStatusObject(
            new StatusBuilder().withCode(HttpURLConnection.HTTP_GONE)
                .withMessage(
                    "410: The event in requested index is outdated and cleared (the requested history has been cleared [3/1]) [2]")
                .build())
            .build();
    }
}
