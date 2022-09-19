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

import by.iba.vfapi.dao.PodEventRepositoryImpl;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PodServiceTest {
    private final KubernetesServer server = new KubernetesServer();
    private static final String APP_NAME = "vf";
    private static final String APP_NAME_LABEL = "testApp";
    private static final String PVC_MOUNT_PATH = "/files";
    private static final Long EVENT_WAIT_PERIOD_MS = 10L;

    @Mock
    private AuthenticationService authenticationServiceMock;
    @Mock
    private PodEventRepositoryImpl podEventRepository;
    @Mock
    private KubernetesService kubernetesService;
    private PodService podService;

    @BeforeEach
    void setUp() {
        server.before();
        this.kubernetesService =
            new KubernetesService(server.getClient(), APP_NAME, APP_NAME_LABEL, PVC_MOUNT_PATH, authenticationServiceMock, podEventRepository);
        this.podService = new PodService(kubernetesService);
    }

    @AfterEach
    void tearDown() {
        server.after();
    }

    @Test
    void testTrackPodEvents() {
        PodStatus podStatus = new PodStatus();
        podStatus.setPhase(K8sUtils.SUCCEEDED_STATUS);
        Pod pod = new PodBuilder()
            .withNewMetadata()
            .withNamespace("vf")
            .withName("pod1")
            .addToLabels(Constants.TYPE, "job")
            .addToLabels(Constants.STARTED_BY, "test_user")
            .withResourceVersion("1")
            .endMetadata()
            .withStatus(podStatus)
            .build();

        server.expect()
            .withPath("/api/v1/namespaces/vf/pods?fieldSelector=metadata.name%3Dpod1&watch=true")
            .andUpgradeToWebSocket()
            .open()
            .waitFor(EVENT_WAIT_PERIOD_MS)
            .andEmit(new WatchEvent(pod, "MODIFIED"))
            .done()
            .once();

        podService.trackPodEvents("vf", "pod1");
    }
}
