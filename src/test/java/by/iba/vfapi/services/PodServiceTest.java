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

import by.iba.vfapi.common.LoadFilePodBuilderService;
import by.iba.vfapi.config.ApplicationConfigurationProperties;
import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dao.LogRepositoryImpl;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.api.model.WatchEventBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.HttpURLConnection;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PodServiceTest {
    private final KubernetesServer server = new KubernetesServer();
    private static final Long EVENT_WAIT_PERIOD_MS = 10L;

    @Mock
    private AuthenticationService authenticationServiceMock;
    @Mock
    private KubernetesService kubernetesService;
    @Mock
    private JobHistoryRepository historyRepository;
    @Mock
    private LogRepositoryImpl logRepository;
    private PodService podService;
    @Mock
    private ApplicationConfigurationProperties appProperties;
    @Mock
    private LoadFilePodBuilderService filePodService;

    @BeforeEach
    void setUp() {
        server.before();
        this.kubernetesService = new KubernetesService(appProperties, server.getClient(), authenticationServiceMock, filePodService);
        this.podService = new PodService(kubernetesService, historyRepository, logRepository);
    }

    @AfterEach
    void tearDown() {
        server.after();
    }

    @Test
    void testTrackPodEvents() {

        when(historyRepository.recordLogs()).thenReturn(true);

        mockAuthenticationService();
        server.expect()
                .withPath("/api/v1/namespaces/vf/pods?fieldSelector=metadata.name%3Dpod1&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(outdatedEvent())
                .done()
                .once();

        podService.trackPodEvents("vf", "pod1");
    }

    private void mockAuthenticationService() {
        UserInfo ui = new UserInfo();
        ui.setSuperuser(true);
        when(authenticationServiceMock.getUserInfo()).thenReturn(Optional.of(ui));
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
