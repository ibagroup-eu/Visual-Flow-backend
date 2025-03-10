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
import by.iba.vfapi.dao.LogRepositoryImpl;
import by.iba.vfapi.dto.LogDto;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.HttpURLConnection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {
    @Mock
    private LogRepositoryImpl logRepository;
    @Mock
    private AuthenticationService authenticationServiceMock;
    private final KubernetesServer server = new KubernetesServer();
    private LogService logService;
    @Mock
    private ApplicationConfigurationProperties appProperties;
    @Mock
    private LoadFilePodBuilderService filePodService;

    @BeforeEach
    void setUp() {
        server.before();
        KubernetesService kubernetesService = new KubernetesService(appProperties, server.getClient(), authenticationServiceMock, filePodService);
        this.logService = new LogService( logRepository, kubernetesService);
    }

    @Test
    void testGetParsedPodLogs() {
        String logs =
            "2020-09-29 11:02:23,180 [shutdown-hook-0] [/] INFO  org.apache.spark.SparkContext - Invoking stop()" +
                " from shutdown hook\n" +
                "AND SOMETHING ELSE\n" +
                "2020-09-29 11:02:23,197 [shutdown-hook-0] [/] INFO  o.s.jetty.server.AbstractConnector - Stopped";

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/pods/pod1/log?pretty=false&container=main")
                .andReturn(HttpURLConnection.HTTP_OK, logs)
                .once();

        List<LogDto> logsObjects = logService.getParsedPodLogs("namespace", "pod1");
        LogDto expected = LogDto
            .builder()
            .message("org.apache.spark.SparkContext - Invoking stop() from shutdown hook\nAND SOMETHING ELSE")
            .level("INFO")
            .timestamp("2020-09-29 11:02:23,180")
            .build();

        assertEquals(2, logsObjects.size(), "Size must be equals to 2");
        assertEquals(expected, logsObjects.get(0), "Logs must be equal to expected");
    }

    @Test
    void testGetParsedHistoryLogs() {
        String logs =
                "2020-09-29 11:02:23,180 [shutdown-hook-0] [/] INFO  org.apache.spark.SparkContext - Invoking stop()" +
                        " from shutdown hook\n" +
                        "AND SOMETHING ELSE\n" +
                        "2020-09-29 11:02:23,197 [shutdown-hook-0] [/] INFO  o.s.jetty.server.AbstractConnector - Stopped";

        when(logRepository.get(anyString())).thenReturn(logs);

        List<LogDto> logsObjects = logService.getParsedHistoryLogs("namespace", "pod1", "key");
        LogDto expected = LogDto
                .builder()
                .message("org.apache.spark.SparkContext - Invoking stop() from shutdown hook\nAND SOMETHING ELSE")
                .level("INFO")
                .timestamp("2020-09-29 11:02:23,180")
                .build();

        assertEquals(2, logsObjects.size(), "Size must be equals to 2");
        assertEquals(expected, logsObjects.get(0), "Logs must be equal to expected");
    }

    @Test
    void testGetCustomContainerLogs() {
        String namespace = "namespace";
        String name = "pod1";

        String logs =
                "2020-09-29 11:02:23,180 [shutdown-hook-0] [/] INFO  org.apache.spark.SparkContext - Invoking stop()" +
                " from shutdown hook\nAND SOMETHING ELSE\n" +
                "2020-09-29 11:02:23,197 [shutdown-hook-0] [/] INFO  o.s.jetty.server.AbstractConnector - Stopped";

        Pod pod = new PodBuilder().withMetadata(new ObjectMetaBuilder()
                        .withNamespace(namespace)
                        .withName(name)
                        .addToLabels("pipelineId", "pod1")
                        .addToLabels("containerNodeId", "nodeId")
                        .build())
                .build();

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/pods?labelSelector=containerNodeId%3DnodeId%2CpipelineId%3Dpod1")
                .andReturn(HttpURLConnection.HTTP_OK, new PodListBuilder().addToItems(pod).build())
                .once();

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/pods?labelSelector=pipelineId%3Dpod1%2CcontainerNodeId%3DnodeId")
                .andReturn(HttpURLConnection.HTTP_OK, new PodListBuilder().addToItems(pod).build())
                .once();

        server
                .expect()
                .get()
                .withPath("/api/v1/namespaces/namespace/pods/pod1/log?pretty=false&container=main")
                .andReturn(HttpURLConnection.HTTP_OK, logs)
                .once();

        List<LogDto> logsObjects = logService.getCustomContainerLogs(namespace, name, "nodeId");
        LogDto expected = LogDto
                .builder()
                .message("org.apache.spark.SparkContext - Invoking stop() from shutdown hook\nAND SOMETHING ELSE")
                .level("INFO")
                .timestamp("2020-09-29 11:02:23,180")
                .build();

        assertEquals(2, logsObjects.size(), "Size must be equals to 2");
        assertEquals(expected, logsObjects.get(0), "Logs must be equal to expected");
    }

}
