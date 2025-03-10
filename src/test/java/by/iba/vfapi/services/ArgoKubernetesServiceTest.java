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
import by.iba.vfapi.dao.PipelineHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.model.argo.CronWorkflow;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.argo.WorkflowList;
import by.iba.vfapi.model.argo.WorkflowStatus;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateList;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.services.utils.K8sUtils;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionNamesBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinitionSpecBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(value = ApplicationConfigurationProperties.class)
class ArgoKubernetesServiceTest {
    private static final Long EVENT_WAIT_PERIOD_MS = 10L;
    private final KubernetesServer server = new KubernetesServer();
    @Mock
    private AuthenticationService authenticationServiceMock;
    @Mock
    private LogRepositoryImpl logRepository;
    private ArgoKubernetesService argoKubernetesService;
    @Autowired
    private ApplicationConfigurationProperties appProperties;
    @Mock
    private LoadFilePodBuilderService filePodService;


    @BeforeEach
    void setUp() {
        server.before();
        CustomResourceDefinition wfCrd = new CustomResourceDefinitionBuilder()
            .withApiVersion("apiextensions.k8s.io/v1beta1")
            .withKind("CustomResourceDefinition")
            .withMetadata(new ObjectMetaBuilder().withName("workflows.argoproj.io").build())
            .withSpec(new CustomResourceDefinitionSpecBuilder()
                          .withGroup("argoproj.io")
                          .withScope("Namespaced")
                          .withVersion("v1alpha1")
                          .withNames(new CustomResourceDefinitionNamesBuilder()
                                         .withPlural("workflows")
                                         .withKind("Workflow")
                                         .build())
                          .build())
            .build();
        CustomResourceDefinition wfTmplCrd = new CustomResourceDefinitionBuilder()
            .withApiVersion("apiextensions.k8s.io/v1beta1")
            .withKind("CustomResourceDefinition")
            .withMetadata(new ObjectMetaBuilder().withName("workflowtemplates.argoproj.io").build())
            .withSpec(new CustomResourceDefinitionSpecBuilder()
                          .withGroup("argoproj.io")
                          .withScope("Namespaced")
                          .withVersion("v1alpha1")
                          .withNames(new CustomResourceDefinitionNamesBuilder()
                                         .withPlural("workflowtemplates")
                                         .withKind("WorkflowTemplate")
                                         .build())
                          .build())
            .build();
        CustomResourceDefinition cronWfCrd = new CustomResourceDefinitionBuilder()
            .withApiVersion("apiextensions.k8s.io/v1beta1")
            .withKind("CustomResourceDefinition")
            .withMetadata(new ObjectMetaBuilder().withName("cronworkflows.argoproj.io").build())
            .withSpec(new CustomResourceDefinitionSpecBuilder()
                          .withGroup("argoproj.io")
                          .withScope("Namespaced")
                          .withVersion("v1alpha1")
                          .withNames(new CustomResourceDefinitionNamesBuilder()
                                         .withPlural("cronworkflows")
                                         .withKind("CronWorkflow")
                                         .build())
                          .build())
            .build();
        server
            .expect()
            .get()
            .withPath("/apis/apiextensions.k8s.io/v1beta1/customresourcedefinitions/workflows.argoproj.io")
            .andReturn(HttpURLConnection.HTTP_OK, wfCrd)
            .once();
        server
            .expect()
            .get()
            .withPath("/apis/apiextensions.k8s.io/v1beta1/customresourcedefinitions/workflowtemplates.argoproj.io")
            .andReturn(HttpURLConnection.HTTP_OK, wfTmplCrd)
            .once();
        server
            .expect()
            .get()
            .withPath("/apis/apiextensions.k8s.io/v1beta1/customresourcedefinitions/cronworkflows.argoproj.io")
            .andReturn(HttpURLConnection.HTTP_OK, cronWfCrd)
            .once();
        argoKubernetesService = new ArgoKubernetesService(
            appProperties,
            server.getClient(),
            authenticationServiceMock,
            logRepository,
            filePodService
        );
    }

    @AfterEach
    void tearDown() {
        server.after();
    }

    private void mockAuthenticationService() {
        UserInfo ui = new UserInfo();
        ui.setSuperuser(true);
        when(authenticationServiceMock.getUserInfo()).thenReturn(Optional.of(ui));
    }

    @Test
    void testCreateOrReplaceWorkflowTemplate() {
        mockAuthenticationService();

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder().withName("wftml").withLabels(Map.of()).build());

        server
            .expect()
            .post()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflowtemplates")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .once();

        argoKubernetesService.createOrReplaceWorkflowTemplate("vf", workflowTemplate);
    }

    @Test
    void testGetAllWorkflowTemplate() {
        mockAuthenticationService();

        WorkflowTemplate workflowTemplate1 = new WorkflowTemplate();
        workflowTemplate1.setMetadata(new ObjectMetaBuilder().withName("wftml1").build());
        WorkflowTemplate workflowTemplate2 = new WorkflowTemplate();
        workflowTemplate2.setMetadata(new ObjectMetaBuilder().withName("wftml2").build());
        WorkflowTemplateList templateList =
            new WorkflowTemplateList().items(List.of(workflowTemplate1, workflowTemplate2));

        server
            .expect()
            .get()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflowtemplates")
            .andReturn(HttpURLConnection.HTTP_OK, templateList)
            .once();

        List<WorkflowTemplate> actual = argoKubernetesService.getAllWorkflowTemplates("vf");

        assertEquals(2, actual.size(), "Size must be equals to expected");
        assertEquals(workflowTemplate1.getSpec(), actual.get(0).getSpec(), "Spec must be equals to expected");
        assertEquals(workflowTemplate1.getMetadata(),
                     actual.get(0).getMetadata(),
                     "Metadata must be equals to expected");
        assertEquals(workflowTemplate2.getSpec(), actual.get(1).getSpec(), "Spec must be equals to expected");
        assertEquals(workflowTemplate2.getMetadata(),
                     actual.get(1).getMetadata(),
                     "Metadata must be equals to expected");
    }

    @Test
    void testGetWorkflowTemplate() {
        mockAuthenticationService();

        WorkflowTemplate template = new WorkflowTemplate();
        template.setMetadata(new ObjectMetaBuilder().withName("id").build());
        server
            .expect()
            .get()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflowtemplates/id")
            .andReturn(HttpURLConnection.HTTP_OK, template)
            .once();

        WorkflowTemplate actual = argoKubernetesService.getWorkflowTemplate("vf", "id");

        assertEquals(template.getMetadata(), actual.getMetadata(), "Metadata must be equals to expected");
    }

    @Test
    void testIsWorkflowTemplateExist() {
        mockAuthenticationService();

        WorkflowTemplate template = new WorkflowTemplate();
        template.setMetadata(new ObjectMetaBuilder().withName("id").addToLabels("name", "wf").build());

        server
                .expect()
                .get()
                .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflowtemplates/id")
                .andReturn(HttpURLConnection.HTTP_OK, template)
                .once();

        boolean workflowExistence = argoKubernetesService.isWorkflowTemplateExist("vf", "id");

        assertTrue(workflowExistence, "WorkflowTemplate existence must be equals to expected");
    }

    @Test
    void testGetWorkflowTemplatesByLabels() {
        mockAuthenticationService();

        WorkflowTemplate workflowTemplate1 = new WorkflowTemplate();
        workflowTemplate1.setMetadata(new ObjectMetaBuilder()
                .withName("wftml1")
                .addToLabels(Constants.NAME, "name1")
                .build());
        WorkflowTemplateList templateList = new WorkflowTemplateList().items(List.of(workflowTemplate1));

        server
            .expect()
            .get()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflowtemplates?labelSelector=name%3Dname1")
            .andReturn(HttpURLConnection.HTTP_OK, templateList)
            .once();

        List<WorkflowTemplate> actual =
            argoKubernetesService.getWorkflowTemplatesByLabels("vf", Map.of(Constants.NAME, "name1"));

        assertEquals(1, actual.size(), "Size must be equals to expected");
        assertEquals(workflowTemplate1.getSpec(), actual.get(0).getSpec(), "Spec must be equals to expected");
        assertEquals(
            workflowTemplate1.getMetadata(),
            actual.get(0).getMetadata(),
            "Metadata must be equals to expected");
    }

    @Test
    void testDeleteWorkflowTemplate() {
        mockAuthenticationService();

        server
            .expect()
            .delete()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflowtemplates?labelSelector=id%3Did")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        argoKubernetesService.deleteWorkflowTemplate("namespaceName", "id");
    }

    @Test
    void testCreateOrReplaceWorkflow() {
        mockAuthenticationService();

        Workflow workflow = new Workflow();
        workflow.setMetadata(new ObjectMetaBuilder().withName("wf").build());

        server
            .expect()
            .post()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflows")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .once();

        argoKubernetesService.createOrReplaceWorkflow("vf", workflow);
    }

    @Test
    void testGetWorkflow() {
        mockAuthenticationService();

        Workflow workflow = new Workflow();
        workflow.setMetadata(new ObjectMetaBuilder().withName("id").addToLabels("name", "wf").build());

        server
            .expect()
            .get()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflows/id")
            .andReturn(HttpURLConnection.HTTP_OK, workflow)
            .once();

        Workflow actual = argoKubernetesService.getWorkflow("vf", "id");

        assertEquals("id", actual.getMetadata().getName(), "Name must be equals to expected");
        assertEquals("wf", actual.getMetadata().getLabels().get("name"), "Label must be equals to expected");
    }

    @Test
    void testGetCronWorkflowsByLabel() {
        mockAuthenticationService();

        Workflow workflow = new Workflow();
        workflow.setMetadata(new ObjectMetaBuilder()
                .withName("id")
                .addToLabels(Constants.CRON_WORKFLOW_POD_LABEL, "wf")
                .build());

        WorkflowList workflowList = new WorkflowList().items(List.of(workflow));

        server
                .expect()
                .get()
                .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflows?labelSelector=workflows.argoproj.io%2Fcron-workflow%3Dwf")
                .andReturn(HttpURLConnection.HTTP_OK, workflowList)
                .once();

        List<Workflow> actual = argoKubernetesService.getCronWorkflowsByLabel("vf", "wf");
        assertEquals(1, actual.size(), "Size must be equals to expected");
        assertEquals("id", actual.get(0).getMetadata().getName(),
                "Name must be equals to expected");
        assertEquals("wf", actual.get(0).getMetadata().getLabels().get(Constants.CRON_WORKFLOW_POD_LABEL),
                "Label must be equals to expected");
    }

    @Test
    void testDeleteWorkflow() {
        mockAuthenticationService();

        server
            .expect()
            .delete()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflows?labelSelector=id%3Did")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        argoKubernetesService.deleteWorkflow("vf", "id");
    }

    @Test
    void testGetCronWorkflow() {
        mockAuthenticationService();

        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setMetadata(new ObjectMetaBuilder().withName("id").addToLabels("name", "wf").build());

        server
            .expect()
            .get()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/cronworkflows/id")
            .andReturn(HttpURLConnection.HTTP_OK, cronWorkflow)
            .once();

        CronWorkflow actual = argoKubernetesService.getCronWorkflow("vf", "id");

        assertEquals("id", actual.getMetadata().getName(), "Name must be equals to expected");
        assertEquals("wf", actual.getMetadata().getLabels().get("name"), "Label must be equals to expected");
    }

    @Test
    void testCreateOrReplaceCronWorkflow() {
        mockAuthenticationService();

        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setMetadata(new ObjectMetaBuilder().withName("wf").build());

        server
            .expect()
            .post()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/cronworkflows")
            .andReturn(HttpURLConnection.HTTP_CREATED, null)
            .once();

        argoKubernetesService.createOrReplaceCronWorkflow("vf", cronWorkflow);
    }

    @Test
    void testDeleteCronWorkflow() {
        mockAuthenticationService();

        server
            .expect()
            .delete()
            .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/workflows?labelSelector=id%3Did")
            .andReturn(HttpURLConnection.HTTP_OK, null)
            .once();

        argoKubernetesService.deleteCronWorkflow("vf", "id");
    }

    @Test
    void testIsCronWorkflowReadyOrExist() {
        mockAuthenticationService();

        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setMetadata(new ObjectMetaBuilder().withName("id").addToLabels("name", "wf").build());

        server
                .expect()
                .get()
                .withPath("/apis/argoproj.io/v1alpha1/namespaces/vf/cronworkflows/id")
                .andReturn(HttpURLConnection.HTTP_OK, cronWorkflow)
                .once();

        boolean cronExistence = argoKubernetesService.isCronWorkflowReadyOrExist("vf", "id");

        assertTrue(cronExistence, "Cron existence must be equals to expected");
    }

    @Test
    void testWatchWorkflow() {
        mockAuthenticationService();
        WorkflowStatus workflowStatus = new WorkflowStatus();
        workflowStatus.setPhase(K8sUtils.SUCCEEDED_STATUS);
        Workflow workflow = new Workflow();
        workflow.setMetadata(new ObjectMetaBuilder()
            .withNamespace("vf")
            .withName("wf")
            .addToLabels(Constants.TYPE, "job")
            .addToLabels(Constants.STARTED_BY, "test_user")
            .withResourceVersion("1")
            .build());
        workflow.setStatus(workflowStatus);
        CountDownLatch latch = mock(CountDownLatch.class);
        PipelineHistoryRepository historyRepository = mock(PipelineHistoryRepository.class);

        server.expect()
            .withPath(
                "/apis/argoproj.io/v1alpha1/namespaces/vf/workflows?fieldSelector=metadata.name%3Dwf&watch=true")
            .andUpgradeToWebSocket()
            .open()
            .waitFor(EVENT_WAIT_PERIOD_MS)
            .andEmit(new WatchEvent(workflow, "MODIFIED"))
            .done()
            .once();

        assertNotNull(argoKubernetesService.watchWorkflow("vf", "wf", historyRepository, latch),
    "Should return Watch event");
    }
}
