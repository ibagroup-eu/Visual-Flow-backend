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
package by.iba.vfapi.services.aspect;

import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.services.ArgoKubernetesService;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.ProjectService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Service;

import static by.iba.vfapi.dto.Constants.TYPE;
import static by.iba.vfapi.dto.Constants.TYPE_JOB;

/**
 * Aspect class.
 * Controls restrictions for demo projects. If the limits are exceeded, stops executing methods.
 */
@Slf4j
@Aspect
@Service
@RequiredArgsConstructor
public class DemoProjectAspect {
    private static final String LIMIT_JOBS_ERR = "Exceeded the limit on jobs.";
    private static final String LIMIT_PIPELINES_ERR = "Exceeded the limit on pipelines.";
    public static final String PROJECT_IS_LOCKED_ERR = "Project '%s' is locked.";
    private final ProjectService projectService;
    private final KubernetesService kubernetesService;
    private final ArgoKubernetesService argoKubernetesService;

    @Pointcut("execution(public * by.iba.vfapi.controllers.*.*(..)) && " +
            "args(projectId,..)")
    public void checkExpirationDatePoint(String projectId) {
    }

    @Pointcut("execution(* by.iba.vfapi.services.KubernetesService.createOrReplaceConfigMap(..)) && " +
            "args(projectId,configMap)")
    public void checkJobsLimit(String projectId, ConfigMap configMap) {
    }

    @Pointcut("execution(* by.iba.vfapi.services.ArgoKubernetesService.createOrReplaceWorkflowTemplate(..)) && " +
            "args(namespaceId,workflowTemplate)")
    public void checkPipelinesLimit(String namespaceId, WorkflowTemplate workflowTemplate) {
    }

    /**
     * Advice for check, if project has expired (for demo projects).
     * Throw {@link BadRequestException} if it is true.
     *
     * @param projectId project id.
     */
    @Before("checkExpirationDatePoint(projectId)")
    public void checkExpirationDateExec(String projectId) {
        LOGGER.debug("Check, if project '{}' has expired...", projectId);
        ProjectResponseDto project = projectService.get(projectId);
        if (project.isLocked()) {
            throw new BadRequestException(String.format(PROJECT_IS_LOCKED_ERR, projectId));
        }
    }

    /**
     * Advice for check, if jobs limit has been exceeded (for demo projects).
     * Throw {@link BadRequestException} if it is true.
     *
     * @param projectId a project ID.
     * @param configMap a new job config map.
     */
    @Before("checkJobsLimit(projectId,configMap)")
    public void checkJobLimitCondition(String projectId, HasMetadata configMap) {
        ProjectResponseDto project = projectService.get(projectId);
        if (project.isDemo()) {
            int jobsCount = kubernetesService.getAllConfigMaps(projectId).size();
            int jobsLimit = project.getDemoLimits().getJobsNumAllowed();
            if (jobsCount > jobsLimit) {
                throw new BadRequestException(LIMIT_JOBS_ERR);
            }
            if (jobsCount == jobsLimit) {
                String jobId = configMap.getMetadata().getName();
                String jobType = configMap.getMetadata().getLabels().get(TYPE);
                if (TYPE_JOB.equals(jobType) && !kubernetesService.isConfigMapReadable(projectId, jobId)) {
                    throw new BadRequestException(LIMIT_JOBS_ERR);
                }
            }
        }
    }

    /**
     * Advice for check, if pipelines limit has been exceeded (for demo projects).
     * Throw {@link BadRequestException} if it is true.
     *
     * @param namespaceId      a project ID.
     * @param workflowTemplate a new pipeline workflow template.
     */
    @Before("checkPipelinesLimit(namespaceId,workflowTemplate)")
    public void checkPipelinesLimitCondition(String namespaceId, HasMetadata workflowTemplate) {
        ProjectResponseDto project = projectService.get(namespaceId);
        if (project.isDemo()) {
            int pipelinesCount = argoKubernetesService.getAllWorkflowTemplates(namespaceId).size();
            int pipelinesLimit = project.getDemoLimits().getPipelinesNumAllowed();
            if (pipelinesCount > pipelinesLimit) {
                throw new BadRequestException(LIMIT_PIPELINES_ERR);
            }
            if (pipelinesCount == pipelinesLimit) {
                String pipelineId = workflowTemplate.getMetadata().getName();
                if (!argoKubernetesService.isWorkflowTemplateReadable(namespaceId, pipelineId)) {
                    throw new BadRequestException(LIMIT_PIPELINES_ERR);
                }
            }
        }
    }
}
