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

import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.history.HistoryResponseDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobRequestDto;
import by.iba.vfapi.dto.jobs.JobResponseDto;
import by.iba.vfapi.dto.jobs.PipelineJobOverviewDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConflictException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.model.history.JobHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.KeyToPathBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * JobService class.
 */
@Slf4j
@Service
// This class contains a sonar vulnerability - java:S1200: Split this class into smaller and more specialized ones to
// reduce its dependencies on other classes from 32 to the maximum authorized 30 or less. This means, that this class
// should not be coupled to too many other classes (Single Responsibility Principle).
public class JobService {

    private final String jobImage;
    private final String jobMaster;
    private final String imagePullSecret;
    private final String pvcMountPath;
    private final String jobConfigMountPath;
    private final String jobConfigFullPath;
    private final String serviceAccount;
    private final KubernetesService kubernetesService;
    private final ArgoKubernetesService argoKubernetesService;
    private final AuthenticationService authenticationService;
    private final PodService podService;
    private final ProjectService projectService;
    private final DependencyHandlerService dependencyHandlerService;
    private final JobHistoryRepository historyRepository;


    // Note that this constructor has the following sonar error: java:S107 - Methods should not have too many
    // parameters. This error has been added to ignore list due to the current inability to solve this problem.
    public JobService(
        @Value("${job.spark.image}") final String jobImage,
        @Value("${job.spark.master}") final String jobMaster,
        @Value("${job.spark.serviceAccount}") final String serviceAccount,
        @Value("${job.imagePullSecret}") final String imagePullSecret,
        @Value("${pvc.mountPath}") final String pvcMountPath,
        @Value("${job.config.mountPath}") final String jobConfigMountPath,
        KubernetesService kubernetesService,
        DependencyHandlerService dependencyHandlerService,
        ArgoKubernetesService argoKubernetesService,
        final AuthenticationService authenticationService,
        final PodService podService,
        final ProjectService projectService,
        final JobHistoryRepository historyRepository) {
        this.jobImage = jobImage;
        this.jobMaster = jobMaster;
        this.serviceAccount = serviceAccount;
        this.imagePullSecret = imagePullSecret;
        this.pvcMountPath = pvcMountPath;
        this.jobConfigMountPath = jobConfigMountPath;
        this.jobConfigFullPath = Paths.get(jobConfigMountPath, Constants.JOB_CONFIG_FILE).toString();
        this.kubernetesService = kubernetesService;
        this.argoKubernetesService = argoKubernetesService;
        this.authenticationService = authenticationService;
        this.podService = podService;
        this.projectService = projectService;
        this.dependencyHandlerService = dependencyHandlerService;
        this.historyRepository = historyRepository;
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
    public String create(final String projectId, @Valid final JobRequestDto jobRequestDto) {
        String jobName = jobRequestDto.getName();
        checkJobName(projectId, null, jobName);

        ConfigMap jobMainCm = jobRequestDto.toConfigMap(UUID.randomUUID().toString(), jobConfigFullPath);
        String jobId = createFromConfigMap(projectId, jobMainCm, true);

        ConfigMap jobDataCm = jobRequestDto.toJobDataConfigMap(jobId);
        createFromConfigMap(projectId, jobDataCm, true);

        projectService.checkConnectionDependencies(projectId, jobId, null,
                DependencyHandlerService.getDefinition(jobMainCm));
        return jobId;
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
            LOGGER.warn("It's ok, there is no job with such id: {}", ex.getLocalizedMessage());
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
    public void update(final String id, final String projectId, @Valid final JobRequestDto jobRequestDto) {
        String jobName = jobRequestDto.getName();
        checkJobName(projectId, id, jobName);

        ConfigMap oldConfigMap = kubernetesService.getConfigMap(projectId, id);
        ConfigMap newConfigMap = new ConfigMapBuilder(jobRequestDto.toConfigMap(id, jobConfigFullPath))
                .addToData(Constants.DEPENDENT_PIPELINE_IDS,
                        oldConfigMap.getData().get(Constants.DEPENDENT_PIPELINE_IDS))
                .build();
        kubernetesService.createOrReplaceConfigMap(projectId, newConfigMap);

        ConfigMap newConfigDataMap = jobRequestDto.toJobDataConfigMap(id);
        kubernetesService.createOrReplaceConfigMap(projectId, newConfigDataMap);

        projectService.checkConnectionDependencies(projectId, id,
                DependencyHandlerService.getDefinition(oldConfigMap),
                DependencyHandlerService.getDefinition(newConfigMap));
        try {
            kubernetesService.deletePod(projectId, id);
            kubernetesService.deletePodsByLabels(projectId, Map.of(Constants.JOB_ID_LABEL,
                                                                   id,
                                                                   Constants.SPARK_ROLE_LABEL,
                                                                   Constants.SPARK_ROLE_EXEC,
                                                                   Constants.PIPELINE_JOB_ID_LABEL,
                                                                   Constants.NOT_PIPELINE_FLAG));
        } catch (ResourceNotFoundException e) {
            LOGGER.error(KubernetesService.NO_POD_MESSAGE, e);
            return;
        }

        List<String> foundParams = ProjectService.findParamsKeysInString(jobRequestDto.getDefinition().toString());
        List<ParamDto> existingParams = projectService.getParams(projectId).getParams();
        for(ParamDto param : existingParams) {
            if(foundParams.contains(param.getKey())) {
                param.getValue().getJobUsages().add(id);
            } else {
                param.getValue().getJobUsages().remove(id);
            }
        }
        projectService.updateParamsWithoutProcessing(projectId, existingParams);
    }

    /**
     * Getting all jobs in project.
     *
     * @param projectId project id
     * @return List of jobs
     */
    public JobOverviewListDto getAll(final String projectId) {
        List<ConfigMap> allConfigMaps = kubernetesService.getAllConfigMaps(projectId);
        boolean accessibleToRun = kubernetesService.isAccessible(projectId, "pods", "",
                Constants.CREATE_ACTION);

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
                    .finishedAt(DateTimeUtils.getFormattedDateTime(K8sUtils
                            .extractTerminatedStateField(podStatus, ContainerStateTerminated::getFinishedAt)));

                jobBuilder.usage(getJobUsage(projectId, jobId, null));
            } catch (ResourceNotFoundException e) {
                LOGGER.warn("Job {} has not started yet: {}", jobId, e.getLocalizedMessage());
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

            appendRunnable(projectId, configMap, jobBuilder::runnable, accessibleToRun);

            jobs.add(jobBuilder.build());
        }

        JobOverviewListDto.JobOverviewListDtoBuilder builder = JobOverviewListDto.builder().jobs(jobs);
        appendEditable(projectId, builder::editable);
        return builder.build();
    }

    private void appendEditable(String projectId, Consumer<Boolean> consumer) {
        consumer.accept(kubernetesService.isAccessible(projectId, "configmaps", "",
                Constants.UPDATE_ACTION));
    }

    private void appendRunnable(String projectId, ConfigMap cmData, Consumer<Boolean> consumer,
                                boolean accessibleToRun) {
        try {
            GraphDto graphDto = new ObjectMapper().readValue(extractJobDataFromCm(projectId, cmData), GraphDto.class);
            consumer.accept(accessibleToRun && !graphDto.getNodes().isEmpty());
        } catch (JsonProcessingException e) {
            throw new InternalProcessingException("Job config is corrupted", e);
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Job config is missing", e);
        }
    }

    public String extractJobDataFromCm(String projectId, ConfigMap cmData) {
        try {
            ConfigMap jobDataCm = kubernetesService.getConfigMap(projectId, cmData.getMetadata().getName() +
                    Constants.JOB_CONFIG_SUFFIX);
            return jobDataCm.getData().get(Constants.JOB_CONFIG_FIELD);
        } catch (ResourceNotFoundException e) {
            LOGGER.warn("Config map for '{}' Job Config is missing: {}. " +
                            "Try to get JOB_CONFIG from the parent config map...",
                    cmData.getMetadata().getName(),
                    e.getLocalizedMessage()
            );
            return cmData.getData().get(Constants.JOB_CONFIG_FIELD);
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
            LOGGER.error("Unable to retrieve metrics for pod {}", jobId);
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
                .finishedAt(DateTimeUtils.getFormattedDateTime(K8sUtils
                        .extractTerminatedStateField(status, ContainerStateTerminated::getFinishedAt)));
        } catch (ResourceNotFoundException e) {
            LOGGER.warn("Job {} has not started yet: {}", id, e.getLocalizedMessage());
        }

        appendRunnable(projectId, configMap, jobResponseDtoBuilder::runnable, accessibleToRun);
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
        ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, id);
        if (!dependencyHandlerService.jobHasDepends(configMap)) {
            throw new BadRequestException("The job is still being used by the pipeline");
        }
        projectService.checkConnectionDependencies(projectId, id,
                DependencyHandlerService.getDefinition(configMap), null);

        kubernetesService.deleteConfigMap(projectId, id + Constants.JOB_CONFIG_SUFFIX);
        kubernetesService.deleteConfigMap(projectId, id);
        kubernetesService.deletePodsByLabels(projectId, Map.of(Constants.JOB_ID_LABEL, id));

        List<ParamDto> existingParams = projectService.getParams(projectId).getParams();
        existingParams.forEach(param -> param.getValue().getJobUsages().remove(id));
        projectService.updateParamsWithoutProcessing(projectId, existingParams);
    }

    /**
     * Configuration and running job.
     *
     * @param projectId project id
     * @param jobId     job id
     */
    public void run(final String projectId, final String jobId) {
        ConfigMap configMap = kubernetesService.getConfigMap(projectId, jobId);
        try {
            kubernetesService.getConfigMap(projectId, configMap.getMetadata().getName() +
                    Constants.JOB_CONFIG_SUFFIX);
        } catch (ResourceNotFoundException e) {
            LOGGER.warn(
                    "Config map for '{}' Job Config is missing: {}. Converting old-format configmap to new-format...",
                    configMap.getMetadata().getName(),
                    e.getLocalizedMessage());
            JobResponseDto jobResponseDto = get(projectId, jobId);
            JobRequestDto reCreatedJobDto = JobRequestDto.builder()
                    .name(jobResponseDto.getName())
                    .params(jobResponseDto.getParams())
                    .definition(jobResponseDto.getDefinition())
                    .build();
            update(jobId, projectId, reCreatedJobDto);
        }
        Pod pod = initializePod(projectId, jobId, configMap);
        try {
            kubernetesService.deletePod(projectId, jobId);
            kubernetesService.deletePodsByLabels(projectId, Map.of(Constants.JOB_ID_LABEL,
                                                                   jobId,
                                                                   Constants.SPARK_ROLE_LABEL,
                                                                   Constants.SPARK_ROLE_EXEC,
                                                                   Constants.PIPELINE_JOB_ID_LABEL,
                                                                   Constants.NOT_PIPELINE_FLAG));
        } catch (ResourceNotFoundException e) {
            LOGGER.error(KubernetesService.NO_POD_MESSAGE, e);
        }
        podService.trackPodEvents(projectId, jobId);
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
     * Retrieve job history
     *
     * @param projectId project id
     * @param id        job id
     * @return list of history
     */
    public List<HistoryResponseDto> getJobHistory(String projectId, String id) {
        Map<String, JobHistory> histories = historyRepository.findAll(projectId + "_" + id);

        return histories
            .entrySet()
            .stream()
            .map(jobHistory -> HistoryResponseDto
                .builder()
                .id(jobHistory.getValue().getId())
                .type(jobHistory.getValue().getType())
                .status(jobHistory.getValue().getStatus())
                .startedAt(jobHistory.getValue().getStartedAt())
                .finishedAt(jobHistory.getValue().getFinishedAt())
                .startedBy(jobHistory.getValue().getStartedBy())
                .logId(jobHistory.getKey())
                .build()
            )
            .collect(Collectors.toList());
    }

    /**
     * Initialize pod
     *
     * @param projectId  project id
     * @param jobId      job id
     * @param configMap  config map
     * @return pod
     */
    private Pod initializePod(String projectId, String jobId, ConfigMap configMap) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(jobId)
                .withNamespace(projectId)
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToLabels(Constants.JOB_ID_LABEL, jobId)
                .addToLabels(Constants.STARTED_BY, authenticationService.getUserInfo().getUsername())
                .endMetadata()
                .withNewSpec()
                .withServiceAccountName(serviceAccount)
                .withNewSecurityContext()
                .withRunAsGroup(K8sUtils.GROUP_ID)
                .withRunAsUser(K8sUtils.USER_ID)
                .withFsGroup(K8sUtils.FS_GROUP_ID)
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
                        new EnvVarBuilder().withName("PVC_NAME").withValue(K8sUtils.PVC_NAME).build(),
                        new EnvVarBuilder().withName("MOUNT_PATH").withValue(pvcMountPath).build(),
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
                .addNewVolumeMount()
                .withName("spark-pvc-volume")
                .withMountPath(pvcMountPath)
                .endVolumeMount()
                .addNewVolumeMount()
                .withName("job-config-data")
                .withMountPath(jobConfigMountPath)
                .endVolumeMount()
                .endContainer()
                .addNewVolume()
                .withName("spark-pvc-volume")
                .withNewPersistentVolumeClaim()
                .withClaimName(K8sUtils.PVC_NAME)
                .endPersistentVolumeClaim()
                .endVolume()
                .addNewVolume()
                .withName("job-config-data")
                .withNewConfigMap()
                .withName(jobId + Constants.JOB_CONFIG_SUFFIX)
                .withItems(new KeyToPathBuilder()
                        .withKey(Constants.JOB_CONFIG_FIELD)
                        .withPath(Constants.JOB_CONFIG_FILE)
                        .build())
                .endConfigMap()
                .endVolume()
                .addNewImagePullSecret(imagePullSecret)
                .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    /**
     * Method for recalculation all params usages in all jobs.
     *
     * @param projectId is a project id.
     * @return true, if there were not any errors during the recalculation.
     */
    public boolean recalculateParamsJobUsages(final String projectId) {
        List<ParamDto> allParams = projectService.getParams(projectId).getParams();
        allParams.forEach(param -> param.getValue().getJobUsages().clear());
        List<JobOverviewDto> allJobs = getAll(projectId).getJobs();
        for(JobOverviewDto jobOverviewDto: allJobs) {
            JobResponseDto jobDto = get(projectId, jobOverviewDto.getId());
            List<String> foundParams = ProjectService.findParamsKeysInString(jobDto.getDefinition().toString());
            if (!foundParams.isEmpty()) {
                allParams
                        .stream()
                        .filter(param -> foundParams.contains(param.getKey()))
                        .forEach(param -> param.getValue().getJobUsages().add(jobOverviewDto.getId()));
            }
        }
        projectService.updateParamsWithoutProcessing(projectId, allParams);
        return true;
    }
}
