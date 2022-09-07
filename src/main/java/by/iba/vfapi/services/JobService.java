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

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.LogDto;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobRequestDto;
import by.iba.vfapi.dto.jobs.JobResponseDto;
import by.iba.vfapi.dto.jobs.PipelineJobOverviewDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConflictException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * JobService class.
 */
@Slf4j
@Service
public class JobService {

    private final String jobImage;
    private final String jobMaster;
    private final String imagePullSecret;
    private final String serviceAccount;
    private final KubernetesService kubernetesService;

    public JobService(
        @Value("${job.spark.image}") final String jobImage,
        @Value("${job.spark.master}") final String jobMaster,
        @Value("${job.spark.serviceAccount}") final String serviceAccount,
        @Value("${job.imagePullSecret}") final String imagePullSecret,
        KubernetesService kubernetesService) {
        this.jobImage = jobImage;
        this.jobMaster = jobMaster;
        this.serviceAccount = serviceAccount;
        this.imagePullSecret = imagePullSecret;
        this.kubernetesService = kubernetesService;
    }

    /**
     * Check is job name unique in project.
     *
     * @param projectId projectId
     * @param jobId     job id
     * @param jobName   job name
     */
    void checkJobName(String projectId, String jobId, String jobName) {
        List<ConfigMap> configMapsByLabels =
            kubernetesService.getConfigMapsByLabels(projectId, Map.of(Constants.NAME, jobName));

        if (configMapsByLabels.size() > 1 ||
            (configMapsByLabels.size() == 1 && !configMapsByLabels.get(0).getMetadata().getName().equals(jobId))) {
            throw new BadRequestException(String.format("Job with name '%s' already exist in project %s",
                                                        jobName,
                                                        projectId));
        }
    }

    /**
     * Creating or replacing job.
     *
     * @param projectId     project id
     * @param jobRequestDto job request dto
     * @return id of job
     */
    public String create(
        final String projectId, @Valid final JobRequestDto jobRequestDto) {
        String jobName = jobRequestDto.getName();
        checkJobName(projectId, null, jobName);
        ConfigMap configMap = jobRequestDto.toConfigMap(UUID.randomUUID().toString());
        return createFromConfigMap(projectId, configMap, true);
    }

    /**
     * Creates a new job from configmap or replaces an existing one
     *
     * @param projectId       id of the project
     * @param configMap       config map
     * @param replaceIfExists determines whether configmap should replace old one if their ids match
     * @return id of the job
     */
    public String createFromConfigMap(final String projectId, final ConfigMap configMap, boolean replaceIfExists) {
        String id = configMap.getMetadata().getName();

        try {
            kubernetesService.getConfigMap(projectId, id);
            if (!replaceIfExists) {
                configMap.getMetadata().setName(UUID.randomUUID().toString());
            }
            return createFromConfigMap(projectId, configMap, replaceIfExists);
        } catch (ResourceNotFoundException ex) {
            LOGGER.info("It's ok, there is no job with such id");
            kubernetesService.createOrReplaceConfigMap(projectId, configMap);
            return id;
        }
    }

    /**
     * Updating job.
     *
     * @param id            job id
     * @param projectId     project id
     * @param jobRequestDto job request dto
     */
    public void update(
        final String id, final String projectId, @Valid final JobRequestDto jobRequestDto) {
        String jobName = jobRequestDto.getName();
        checkJobName(projectId, id, jobName);

        ConfigMap newConfigMap = jobRequestDto.toConfigMap(id);
        kubernetesService.createOrReplaceConfigMap(projectId, newConfigMap);
        try {
            kubernetesService.deletePod(projectId, id);
            kubernetesService.deletePodsByLabels(projectId, Map.of(Constants.JOB_ID_LABEL,
                                                                   id,
                                                                   Constants.SPARK_ROLE_LABEL,
                                                                   Constants.SPARK_ROLE_EXEC,
                                                                   Constants.PIPELINE_JOB_ID_LABEL,
                                                                   Constants.NOT_PIPELINE_FLAG));
        } catch (ResourceNotFoundException e) {
            LOGGER.info(KubernetesService.NO_POD_MESSAGE, e);
        }
    }

    /**
     * Getting all jobs in project.
     *
     * @param projectId project id
     * @return List of jobs
     */
    public JobOverviewListDto getAll(final String projectId) {
        List<ConfigMap> allConfigMaps = kubernetesService.getAllConfigMaps(projectId);
        boolean accessibleToRun = kubernetesService.isAccessible(projectId, "pods", "", Constants.CREATE_ACTION);

        List<JobOverviewDto> jobs = new ArrayList<>(allConfigMaps.size());
        for (ConfigMap configMap : allConfigMaps) {
            ObjectMeta metadata = configMap.getMetadata();
            String jobId = metadata.getName();

            JobOverviewDto.JobOverviewDtoBuilder jobBuilder = JobOverviewDto.fromConfigMap(configMap);

            try {
                PodStatus podStatus = kubernetesService.getPodStatus(projectId, jobId);
                jobBuilder
                    .startedAt(DateTimeUtils.getFormattedDateTime(podStatus.getStartTime()))
                    .status(podStatus.getPhase())
                    .finishedAt(DateTimeUtils.getFormattedDateTime(K8sUtils.extractTerminatedStateField(podStatus,
                                                                                                        ContainerStateTerminated::getFinishedAt)));

                jobBuilder.usage(getJobUsage(projectId, jobId, null));
            } catch (ResourceNotFoundException e) {
                LOGGER.warn("Job {} has not started yet", jobId);
                jobBuilder.status(K8sUtils.DRAFT_STATUS);
            }
            List<Pod> workflowPods = kubernetesService.getWorkflowPods(projectId, jobId);

            List<PipelineJobOverviewDto> pipelineJobOverviewDtos = new ArrayList<>(workflowPods.size());
            for (Pod workflowPod : workflowPods) {
                PipelineJobOverviewDto pipelineJobOverviewDto = PipelineJobOverviewDto
                    .fromPod(workflowPod)
                    .usage(getJobUsage(projectId, jobId, workflowPod.getMetadata().getName()))
                    .build();
                pipelineJobOverviewDtos.add(pipelineJobOverviewDto);
            }

            jobBuilder.pipelineInstances(pipelineJobOverviewDtos);

            appendRunnable(configMap.getData(), jobBuilder::runnable, accessibleToRun);

            jobs.add(jobBuilder.build());
        }

        JobOverviewListDto.JobOverviewListDtoBuilder builder = JobOverviewListDto.builder().jobs(jobs);
        appendEditable(projectId, builder::editable);
        return builder.build();
    }

    private void appendEditable(String projectId, Consumer<Boolean> consumer) {
        consumer.accept(kubernetesService.isAccessible(projectId, "configmaps", "", Constants.UPDATE_ACTION));
    }

    private void appendRunnable(Map<String, String> cmData, Consumer<Boolean> consumer, boolean accessibleToRun) {
        String jobConfigField = cmData.get(Constants.JOB_CONFIG_FIELD);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            GraphDto graphDto = objectMapper.readValue(jobConfigField, GraphDto.class);
            consumer.accept(accessibleToRun && !graphDto.getNodes().isEmpty());
        } catch (JsonProcessingException e) {
            throw new InternalProcessingException("Job config is corrupted", e);
        }
    }

    private ResourceUsageDto getJobUsage(final String projectId, final String jobId, final String pipelineJobId) {
        Map<String, String> labels = new HashMap<>(Map.of(Constants.JOB_ID_LABEL,
                                                          jobId,
                                                          Constants.SPARK_ROLE_LABEL,
                                                          Constants.SPARK_ROLE_EXEC));
        labels.put(Constants.PIPELINE_JOB_ID_LABEL,
                   Objects.requireNonNullElse(pipelineJobId, Constants.NOT_PIPELINE_FLAG));

        try {
            List<String> executors = kubernetesService
                .getPodsByLabels(projectId, labels)
                .stream()
                .map(Pod::getMetadata)
                .map(ObjectMeta::getName)
                .collect(Collectors.toList());

            List<PodMetrics> podMetrics = new ArrayList<>(executors.size() + 1);

            for (String execName : executors) {
                podMetrics.add(kubernetesService.topPod(projectId, execName));
            }

            podMetrics.add(kubernetesService.topPod(projectId, jobId));

            return ResourceUsageDto
                .usageFromMetricsAndQuota(podMetrics,
                                          kubernetesService.getResourceQuota(projectId, Constants.QUOTA_NAME))
                .build();
        } catch (KubernetesClientException e) {
            if (!e.getStatus().getCode().equals(HttpStatus.NOT_FOUND.value())) {
                throw e;
            }
            LOGGER.warn("Unable to retrieve metrics for pod {}", jobId);
            return ResourceUsageDto.builder().build();
        }
    }

    /**
     * Getting job by id.
     *
     * @param projectId project id
     * @param id        job id
     * @return job graph
     */
    public JobResponseDto get(final String projectId, final String id) {
        ConfigMap configMap = kubernetesService.getConfigMap(projectId, id);
        boolean accessibleToRun = kubernetesService.isAccessible(projectId, "pods", "", Constants.CREATE_ACTION);

        JobResponseDto.JobResponseDtoBuilder jobResponseDtoBuilder =
            JobResponseDto.fromConfigMap(configMap).status(K8sUtils.DRAFT_STATUS);

        try {
            PodStatus status = kubernetesService.getPodStatus(projectId, id);
            jobResponseDtoBuilder
                .status(status.getPhase())
                .startedAt(DateTimeUtils.getFormattedDateTime(status.getStartTime()))
                .finishedAt(DateTimeUtils.getFormattedDateTime(K8sUtils.extractTerminatedStateField(status,
                                                                                                    ContainerStateTerminated::getFinishedAt)));
        } catch (ResourceNotFoundException e) {
            LOGGER.warn("Job {} has not started yet", id);
        }

        appendRunnable(configMap.getData(), jobResponseDtoBuilder::runnable, accessibleToRun);
        appendEditable(projectId, jobResponseDtoBuilder::editable);

        return jobResponseDtoBuilder.build();
    }

    /**
     * Deleting job.
     *
     * @param projectId project id
     * @param id        job id
     */
    public void delete(final String projectId, final String id) {
        kubernetesService.deleteConfigMap(projectId, id);
        kubernetesService.deletePodsByLabels(projectId, Map.of(Constants.JOB_ID_LABEL, id));
    }

    /**
     * Configuration and running job.
     *
     * @param projectId project id
     * @param jobId     job id
     */
    public void run(final String projectId, final String jobId) {
        ConfigMap configMap = kubernetesService.getConfigMap(projectId, jobId);
        Pod pod = new PodBuilder()
            .withNewMetadata()
            .withName(jobId)
            .withNamespace(projectId)
            .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
            .addToLabels(Constants.JOB_ID_LABEL, jobId)
            .endMetadata()
            .withNewSpec()
            .withServiceAccountName(serviceAccount)
            .withNewSecurityContext()
            .withRunAsGroup(K8sUtils.GROUP_ID)
            .withRunAsUser(K8sUtils.USER_ID)
            .endSecurityContext()
            .addNewContainer()
            .withName(K8sUtils.JOB_CONTAINER)
            .withEnv(new EnvVarBuilder()
                         .withName("POD_IP")
                         .withNewValueFrom()
                         .editOrNewFieldRef()
                         .withApiVersion("v1")
                         .withFieldPath("status.podIP")
                         .endFieldRef()
                         .endValueFrom()
                         .build(),
                     new EnvVarBuilder().withName("IMAGE_PULL_SECRETS").withValue(imagePullSecret).build(),
                     new EnvVarBuilder().withName("JOB_MASTER").withValue(jobMaster).build(),
                     new EnvVarBuilder().withName("POD_NAME").withValue(jobId).build(),
                     new EnvVarBuilder().withName("JOB_ID").withValue(jobId).build(),
                     new EnvVarBuilder()
                         .withName("PIPELINE_JOB_ID")
                         .withValue(Constants.NOT_PIPELINE_FLAG)
                         .build(),
                     new EnvVarBuilder().withName("JOB_IMAGE").withValue(jobImage).build(),
                     new EnvVarBuilder().withName("POD_NAMESPACE").withValue(projectId).build())
            .withEnvFrom(new EnvFromSourceBuilder()
                             .withNewConfigMapRef()
                             .withName(jobId)
                             .endConfigMapRef()
                             .build(),
                         new EnvFromSourceBuilder()
                             .withNewSecretRef()
                             .withName(ParamsDto.SECRET_NAME)
                             .endSecretRef()
                             .build())
            .withResources(K8sUtils.getResourceRequirements(configMap.getData()))
            .withCommand("/opt/spark/work-dir/entrypoint.sh")
            .withImage(jobImage)
            .withImagePullPolicy("Always")
            .endContainer()
            .addNewImagePullSecret(imagePullSecret)
            .withRestartPolicy("Never")
            .endSpec()
            .build();
        try {
            kubernetesService.deletePod(projectId, jobId);
            kubernetesService.deletePodsByLabels(projectId, Map.of(Constants.JOB_ID_LABEL,
                                                                   jobId,
                                                                   Constants.SPARK_ROLE_LABEL,
                                                                   Constants.SPARK_ROLE_EXEC,
                                                                   Constants.PIPELINE_JOB_ID_LABEL,
                                                                   Constants.NOT_PIPELINE_FLAG));
        } catch (ResourceNotFoundException e) {
            LOGGER.info(KubernetesService.NO_POD_MESSAGE, e);
        }

        kubernetesService.createPod(projectId, pod);
    }

    /**
     * Stop job.
     *
     * @param projectId project id
     * @param id        job id
     */
    public void stop(final String projectId, final String id) {
        PodStatus podStatus = kubernetesService.getPodStatus(projectId, id);
        if ("Running".equals(podStatus.getPhase())) {
            kubernetesService.stopPod(projectId, id);
        } else if ("Pending".equals(podStatus.getPhase())) {
            kubernetesService.deletePod(projectId, id);
        } else {
            throw new ConflictException("Job is not running");
        }
    }

    /**
     * Retrieve job logs
     *
     * @param projectId project id
     * @param id        job id
     * @return list of log entries
     */
    public List<LogDto> getJobLogs(String projectId, String id) {
        return kubernetesService.getParsedPodLogs(projectId, id);
    }
}
