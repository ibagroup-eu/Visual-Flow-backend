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
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.model.argo.CronWorkflow;
import by.iba.vfapi.model.argo.CronWorkflowList;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.argo.WorkflowList;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateList;
import by.iba.vfapi.model.history.AbstractHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.services.watchers.WorkflowWatcher;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class ArgoKubernetesService extends KubernetesService {

    @Value("${argo.requests.cpu}")
    private String argoExecutorRequestsCpu;
    @Value("${argo.requests.memory}")
    private String argoExecutorRequestsMemory;
    @Value("${argo.limits.cpu}")
    private String argoExecutorLimitsCpu;
    @Value("${argo.limits.memory}")
    private String argoExecutorLimitsMemory;
    @Value("${argo.ttlStrategy.secondsAfterCompletion}")
    private String secondsAfterCompletion;
    @Value("${argo.ttlStrategy.secondsAfterSuccess}")
    private String secondsAfterSuccess;
    @Value("${argo.ttlStrategy.secondsAfterFailure}")
    private String secondsAfterFailure;
    private LogRepositoryImpl logRepository;

    public ArgoKubernetesService(
        NamespacedKubernetesClient client,
        @Value("${namespace.app}") String appName,
        @Value("${namespace.label}") String appNameLabel,
        @Value("${pvc.mountPath}") final String pvcMountPath,
        @Value("${job.imagePullSecret}") final String imagePullSecret,
        AuthenticationService authenticationService,
        LogRepositoryImpl logRepository) {
        super(client, appName, appNameLabel, pvcMountPath, imagePullSecret, authenticationService);
        this.logRepository = logRepository;
    }

    private MixedOperation<CronWorkflow, CronWorkflowList, Resource<CronWorkflow>> getCronWorkflowCrdClient(
        NamespacedKubernetesClient k8sClient) {
        return k8sClient.customResources(CronWorkflow.class, CronWorkflowList.class);
    }

    private MixedOperation<Workflow, WorkflowList, Resource<Workflow>> getWorkflowCrdClient(
        NamespacedKubernetesClient k8sClient) {
        return k8sClient.customResources(Workflow.class, WorkflowList.class);
    }

    private MixedOperation<WorkflowTemplate, WorkflowTemplateList, Resource<WorkflowTemplate>> getWorkflowTemplateCrdClient(
        NamespacedKubernetesClient k8sClient) {
        return k8sClient.customResources(WorkflowTemplate.class, WorkflowTemplateList.class);
    }

    /**
     * Create or replace workflowTemplate.
     *
     * @param namespaceId      namespace id
     * @param workflowTemplate new workflowTemplate
     */
    public void createOrReplaceWorkflowTemplate(
        final String namespaceId, final WorkflowTemplate workflowTemplate) {
        workflowTemplate.getMetadata().getLabels().put(K8sUtils.APP, appNameLabel);

        authenticatedCall(authenticatedClient -> getWorkflowTemplateCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .createOrReplace(workflowTemplate));
    }

    /**
     * Getting all workflowTemplates in namespace.
     *
     * @param namespaceId namespace id
     * @return List with all workflowTemplates
     */
    public List<WorkflowTemplate> getAllWorkflowTemplates(final String namespaceId) {
        return authenticatedCall(authenticatedClient -> getWorkflowTemplateCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .list()
            .getItems());
    }

    /**
     * Getting workflowTemplates by labels.
     *
     * @param namespaceId namespace name
     * @param labels      map of labels
     * @return configmap
     */
    public List<WorkflowTemplate> getWorkflowTemplatesByLabels(
        final String namespaceId, final Map<String, String> labels) {
        return authenticatedCall(authenticatedClient -> getWorkflowTemplateCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withLabels(labels)
            .list()
            .getItems());
    }

    /**
     * Getting workflowTemplate by name.
     *
     * @param namespaceId namespace name
     * @param name        workflowTemplate name
     * @return workflowTemplate
     */
    public WorkflowTemplate getWorkflowTemplate(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> getWorkflowTemplateCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withName(name)
            .require());
    }

    /**
     * Getting existence check of the workflowTemplate.
     *
     * @param namespaceId namespace name
     * @param name        workflowTemplate name
     * @return workflowTemplate
     */
    public boolean isWorkflowTemplateExist(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> getWorkflowTemplateCrdClient(authenticatedClient)
                .inNamespace(namespaceId)
                .withName(name)
                .isReady());
    }

    /**
     * Delete workflowTemplate by name.
     *
     * @param namespaceId namespace name
     * @param name        workflowTemplate name
     */
    public void deleteWorkflowTemplate(final String namespaceId, final String name) {
        authenticatedCall(authenticatedClient -> getWorkflowTemplateCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withName(name)
            .delete());
    }

    /**
     * Create or replace workflow.
     *
     * @param namespaceId namespace id
     * @param workflow    new workflowTemplate
     */
    public void createOrReplaceWorkflow(final String namespaceId, final Workflow workflow) {
        authenticatedCall(authenticatedClient -> getWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .createOrReplace(workflow));
    }

    /**
     * Delete workflow by name.
     *
     * @param namespaceId namespace name
     * @param name        workflow name
     */
    public void deleteWorkflow(final String namespaceId, final String name) {
        authenticatedCall(authenticatedClient -> getWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withName(name)
            .delete());
    }

    /**
     * Getting workflow by name.
     *
     * @param namespaceId namespace name
     * @param name        workflow name
     * @return Workflow
     */
    public Workflow getWorkflow(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> getWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withName(name)
            .require());
    }

    /**
     * Getting cron workflows by label.
     *
     * @param namespaceId namespace name
     * @param name        workflow name
     * @return list of cron workflows
     */
    public List<Workflow> getCronWorkflowsByLabel(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> getWorkflowCrdClient(authenticatedClient)
                .inNamespace(namespaceId)
                .withLabel(Constants.CRON_WORKFLOW_POD_LABEL, name)
                .list()
                .getItems());
    }

    /**
     * Create or replace cron workflow.
     *
     * @param namespaceId  namespace id
     * @param cronWorkflow new cronWorkflow
     */
    public void createOrReplaceCronWorkflow(final String namespaceId, final CronWorkflow cronWorkflow) {
        authenticatedCall(authenticatedClient -> getCronWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .createOrReplace(cronWorkflow));
    }

    /**
     * Delete cron workflow by name.
     *
     * @param namespaceId namespace name
     * @param name        cron workflow name
     */
    public void deleteCronWorkflow(final String namespaceId, final String name) {
        authenticatedCall(authenticatedClient -> getCronWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withName(name)
            .delete());
    }

    /**
     * Getting cron workflow by name.
     *
     * @param namespaceId namespace name
     * @param name        workflow name
     * @return Workflow
     */
    public CronWorkflow getCronWorkflow(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> getCronWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withName(name)
            .require());
    }

    /**
     * Getting readiness check or existence check of the cron.
     *
     * @param namespaceId namespace name
     * @param name        workflow name
     * @return boolean
     */
    public boolean isCronWorkflowReadyOrExist(final String namespaceId, final String name) {
        return authenticatedCall(authenticatedClient -> getCronWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespaceId)
            .withName(name)
            .isReady());
    }

    /**
     * Monitors workflow's events in namespace.
     *
     * @param namespace         namespace
     * @param workflowName      workflow's name
     * @param historyRepository DAO for history
     * @param latch             latch
     * @return Watch
     */
    public Watch watchWorkflow(final String namespace, final String workflowName,
                               final PipelineHistoryRepository<? extends AbstractHistory> historyRepository,
                               final CountDownLatch latch) {
        return authenticatedCall(authenticatedClient -> getWorkflowCrdClient(authenticatedClient)
            .inNamespace(namespace)
            .withName(workflowName)
            .watch(new WorkflowWatcher(historyRepository, logRepository, latch, this)));
    }
}


