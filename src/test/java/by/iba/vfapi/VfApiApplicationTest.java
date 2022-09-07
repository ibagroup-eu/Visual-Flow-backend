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
package by.iba.vfapi;

import by.iba.vfapi.controllers.JobController;
import by.iba.vfapi.controllers.PipelineController;
import by.iba.vfapi.controllers.ProjectController;
import by.iba.vfapi.controllers.TransferController;
import by.iba.vfapi.controllers.UserController;
import by.iba.vfapi.services.ArgoKubernetesService;
import by.iba.vfapi.services.JobService;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.PipelineService;
import by.iba.vfapi.services.ProjectService;
import by.iba.vfapi.services.TransferService;
import by.iba.vfapi.services.UserService;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.services.auth.OAuthService;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class VfApiApplicationTest {
    private final static KubernetesMockServer K8S_SERVER = new KubernetesMockServer();
    @Autowired
    private ProjectController projectController;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private UserController userController;
    @Autowired
    private UserService userService;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private OAuthService oAuthService;
    @Autowired
    private KubernetesService kubernetesService;
    @Autowired
    private KubernetesClient kubernetesClient;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private PipelineController pipelineController;
    @Autowired
    private JobController jobController;
    @Autowired
    private TransferController transferController;
    @Autowired
    private JobService jobService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private ArgoKubernetesService argoKubernetesService;
    @Autowired
    private PipelineService pipelineService;

    {
        K8S_SERVER.init();
        kubernetesClient = K8S_SERVER.createClient();
        CustomResourceDefinition customResourceDefinition = new CustomResourceDefinitionBuilder().build();
        K8S_SERVER
            .expect()
            .get()
            .withPath("/apis/apiextensions.k8s.io/v1beta1/customresourcedefinitions/workflows.argoproj.io")
            .andReturn(200, customResourceDefinition)
            .always();
        K8S_SERVER
            .expect()
            .get()
            .withPath("/apis/apiextensions.k8s.io/v1beta1/customresourcedefinitions/workflowtemplates.argoproj.io")
            .andReturn(200, customResourceDefinition)
            .always();
        K8S_SERVER
            .expect()
            .get()
            .withPath("/apis/apiextensions.k8s.io/v1beta1/customresourcedefinitions/cronworkflows.argoproj.io")
            .andReturn(200, customResourceDefinition)
            .always();
        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY,
                           kubernetesClient.getConfiguration().getMasterUrl());
        System.setProperty(Config.KUBERNETES_TRUST_CERT_SYSTEM_PROPERTY, "true");
    }

    @Test
    void testContextLoads() {
        assertNotNull(projectController);
        assertNotNull(projectService);
        assertNotNull(userController);
        assertNotNull(userService);
        assertNotNull(authenticationService);
        assertNotNull(oAuthService);
        assertNotNull(kubernetesService);
        assertNotNull(restTemplate);
        assertNotNull(kubernetesClient);
        assertNotNull(pipelineController);
        assertNotNull(jobController);
        assertNotNull(transferController);
        assertNotNull(jobService);
        assertNotNull(transferService);
        assertNotNull(argoKubernetesService);
        assertNotNull(pipelineService);
    }
}
