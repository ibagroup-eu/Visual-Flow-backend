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

import by.iba.vfapi.config.ApplicationConfigurationProperties;
import by.iba.vfapi.dao.JobHistoryRepository;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.DataSource;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.history.HistoryResponseDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.PipelineJobOverviewDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConflictException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.model.history.JobHistory;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.services.utils.GraphUtils;
import by.iba.vfapi.services.utils.JobUtils;
import by.iba.vfapi.services.utils.K8sUtils;
import by.iba.vfapi.services.utils.ParamUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
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
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static by.iba.vfapi.dto.Constants.*;

/**
 * JobService class.
 */
@Slf4j
@Service
// This class contains a sonar vulnerability - java:S1200: Split this class into smaller and more specialized ones to
// reduce its dependencies on other classes from 32 to the maximum authorized 30 or less. This means, that this class
// should not be coupled to too many other classes (Single Responsibility Principle).
public class JobService {

    private final String jobConfigFullPath;
    private final KubernetesService kubernetesService;
    private final ArgoKubernetesService argoKubernetesService;
    private final AuthenticationService authenticationService;
    private final PodService podService;
    private final ProjectService projectService;
    private final DependencyHandlerService dependencyHandlerService;
    private final JobHistoryRepository historyRepository;
    private final JobSessionService jobSessionService;
    private final ValidatorService validatorService;
    private final ApplicationConfigurationProperties appProperties;


    // Note that this constructor has the following sonar error: java:S107 - Methods should not have too many
    // parameters. This error has been added to ignore list due to the current inability to solve this problem.
    public JobService(
            KubernetesService kubernetesService,
            DependencyHandlerService dependencyHandlerService,
            ArgoKubernetesService argoKubernetesService,
            final AuthenticationService authenticationService,
            final PodService podService,
            final ProjectService projectService,
            final JobHistoryRepository historyRepository, JobSessionService jobSessionService,
            final ValidatorService validatorService,
            final ApplicationConfigurationProperties appProperties) {
        this.jobSessionService = jobSessionService;
        this.appProperties = appProperties;
        this.jobConfigFullPath = Paths.get(appProperties.getJob().getConfig().getMountPath(),
                Constants.JOB_CONFIG_FILE).toString();
        this.kubernetesService = kubernetesService;
        this.argoKubernetesService = argoKubernetesService;
        this.authenticationService = authenticationService;
        this.podService = podService;
        this.projectService = projectService;
        this.validatorService = validatorService;
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
    void checkJobName(String projectId, String jobId, @Nullable String jobName) {
        if (jobName == null) {
            throw new BadRequestException("Job's name isn't defined");
        }
        List<ConfigMap> configMapsByLabels =
                kubernetesService.getConfigMapsByLabels(projectId, Map.of(NAME, jobName));

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
    public String create(final String projectId, @Valid JobDto jobRequestDto) {
        String jobName = jobRequestDto.getName();
        checkJobName(projectId, null, jobName);

        jobRequestDto.setDefinition(JobUtils.parseJobDefinition(jobRequestDto.getDefinition()));
        String jobId = UUID.randomUUID().toString();
        ConfigMap jobMainCm = K8sUtils.toConfigMap(jobId, jobRequestDto, jobConfigFullPath);

        ProjectResponseDto projectDto = projectService.get(projectId);
        Map<String, List<DataSource>> sourcesToShow = null;
        if (projectDto.getDemoLimits() != null) {
            sourcesToShow = projectDto.getDemoLimits().getSourcesToShow();
        }
        ConfigMap jobDataCm = toJobDataConfigMap(jobId, jobRequestDto, sourcesToShow);
        ConfigMap jobDefCm = toJobDefConfigMap(jobRequestDto, jobId);

        createFromConfigMap(projectId, jobMainCm, true);
        createFromConfigMap(projectId, jobDataCm, true);
        createFromConfigMap(projectId, jobDefCm, true);

        projectService.checkConnectionDependencies(projectId, jobId, null,
                DependencyHandlerService.getJobDefinition(projectId, jobDefCm, kubernetesService));
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
    public void update(final String id, final String projectId, @Valid JobDto jobRequestDto) {
        updateConfigMaps(id, projectId, jobRequestDto);
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

        updateParams(id, projectId, jobRequestDto.getDefinition());
    }

    void updateConfigMaps(String id, String projectId, JobDto jobRequestDto) {
        String jobName = jobRequestDto.getName();
        checkJobName(projectId, id, jobName);

        jobRequestDto.setDefinition(JobUtils.parseJobDefinition(jobRequestDto.getDefinition()));

        ConfigMap oldConfigMap = kubernetesService.getConfigMap(projectId, id);
        ConfigMap newConfigMap = new ConfigMapBuilder(K8sUtils.toConfigMap(id, jobRequestDto, jobConfigFullPath))
                .addToData(Constants.DEPENDENT_PIPELINE_IDS,
                        oldConfigMap.getData().get(Constants.DEPENDENT_PIPELINE_IDS))
                .build();

        ProjectResponseDto projectDto = projectService.get(projectId);
        Map<String, List<DataSource>> sourcesToShow = null;
        if (projectDto.getDemoLimits() != null) {
            sourcesToShow = projectDto.getDemoLimits().getSourcesToShow();
        }
        ConfigMap newConfigDataMap = toJobDataConfigMap(id, jobRequestDto, sourcesToShow);
        ConfigMap newConfigDefMap = toJobDefConfigMap(jobRequestDto, id);

        kubernetesService.createOrReplaceConfigMap(projectId, newConfigMap);
        kubernetesService.createOrReplaceConfigMap(projectId, newConfigDataMap);
        kubernetesService.createOrReplaceConfigMap(projectId, newConfigDefMap);

        projectService.checkConnectionDependencies(projectId, id,
                DependencyHandlerService.getJobDefinition(projectId, oldConfigMap, kubernetesService),
                DependencyHandlerService.getJobDefinition(projectId, newConfigMap, kubernetesService));
    }

    /**
     * Update connection details for all jobs from connection.
     *
     * @param connectDto connectDto
     * @param projectId  project id
     */
    public void updateConnectionDetails(final @Valid ConnectDto connectDto, final String projectId) {
        Optional<JsonNode> optJobIdsNode = Optional.ofNullable(connectDto.getValue().get(Constants.DEPENDENT_JOB_IDS));
        if (optJobIdsNode.isPresent()) {
            for (JsonNode nodeJobId : optJobIdsNode.get()) {
                String jobId = nodeJobId.textValue();

                try {
                    JobDto jobDto = getById(projectId, jobId);

                    JsonNode jobDefinition = appendConnectionDetails(jobDto.getDefinition(), connectDto);
                    JobDto reCreatedJobDto = JobDto.builder()
                            .name(jobDto.getName())
                            .params(jobDto.getParams())
                            .definition(JobUtils.parseJobDefinition(jobDefinition))
                            .build();

                    update(jobId, projectId, reCreatedJobDto);
                } catch (ResourceNotFoundException resourceNotFoundException) {
                    LOGGER.info(
                            "Job '{}' doesn't exist in project '{}'. " +
                                    "But exist in connection '{}' as dependency: {}",
                            jobId,
                            projectId,
                            connectDto.getKey(),
                            resourceNotFoundException.getLocalizedMessage());
                }
            }
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
        boolean accessibleToRun = kubernetesService.isAccessible(projectId, "pods", "",
                Constants.CREATE_ACTION);

        List<JobOverviewDto> jobs = new ArrayList<>(allConfigMaps.size());
        for (ConfigMap configMap : allConfigMaps) {
            ObjectMeta metadata = configMap.getMetadata();
            String jobId = metadata.getName();

            JobOverviewDto.JobOverviewDtoBuilder jobBuilder = JobUtils.convertConfigMapToJobOverview(configMap);

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
                PipelineJobOverviewDto pipelineJobOverviewDto = JobUtils
                        .convertPodToJob(workflowPod)
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
        return JobUtils.withPipelineJobs(builder.build());
    }

    private static JsonNode appendConnectionDetails(JsonNode nodeJobDefinition, ConnectDto connectDto) {
        for (JsonNode values : nodeJobDefinition.get(Constants.GRAPH_LABEL)) {
            Optional<JsonNode> optConnectionId = Optional.ofNullable(values.get(Constants.VALUE_LABEL)
                            .get(Constants.CONNECTION_ID_LABEL))
                    .filter((JsonNode connectionId) -> connectionId.textValue()
                            .equals(connectDto.getKey()));
            if (optConnectionId.isPresent()) {
                ObjectNode nodeValue = (ObjectNode) values.get(Constants.VALUE_LABEL);
                nodeValue.put(Constants.CONNECTION_ID_LABEL, connectDto.getKey());

                connectDto.getValue().fieldNames().forEachRemaining((String field) ->
                        nodeValue.set(field, connectDto.getValue().get(field))
                );
            }
        }
        return nodeJobDefinition;
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

    private JobDto get(String projectId, ConfigMap configMap) {
        String id = configMap.getMetadata().getName();
        boolean accessibleToRun = kubernetesService.isAccessible(projectId, "pods", "", Constants.CREATE_ACTION);

        JobDto.JobDtoBuilder jobResponseDtoBuilder =
                JobUtils.convertConfigMapToJobResponse(configMap).status(K8sUtils.DRAFT_STATUS);

        jobResponseDtoBuilder.definition(DependencyHandlerService
                .getJobDefinition(projectId, configMap, kubernetesService));

        try {
            Pod pod = kubernetesService.getPod(projectId, id);
            PodStatus status = pod.getStatus();
            jobResponseDtoBuilder
                    .runId(pod.getMetadata().getUid())
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
     * Getting job by id.
     *
     * @param projectId project id
     * @param id        job id
     * @return job graph
     */
    public JobDto getById(String projectId, String id) {
        return get(projectId, kubernetesService.getConfigMap(projectId, id));
    }

    /**
     * Getting job's ID by name.
     *
     * @param projectId project id
     * @param name        job name
     * @return job graph
     */
    public String getIdByName(String projectId, String name) {
        return Optional.ofNullable(kubernetesService.getConfigMapByLabel(projectId, NAME, name))
                .map(cm -> cm.getMetadata().getName())
                .orElse(null);
    }

    /**
     * Method to check, if job exists.
     *
     * @param projectId is project ID;
     * @param id        is job's ID;
     * @return true, if this job exists.
     */
    public boolean checkIfJobExists(String projectId, String id) {
        return kubernetesService.isConfigMapReadable(projectId, id);
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
                DependencyHandlerService.getJobDefinition(projectId, configMap, kubernetesService), null);

        kubernetesService.deleteConfigMap(projectId, id + Constants.JOB_DEF_SUFFIX);
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
     * @param projectId   project id
     * @param jobId       job id
     * @param interactive interactive mode
     * @return Pod uuid
     */
    public String run(final String projectId, final String jobId, boolean interactive) {
        JobDto jobDto = getById(projectId, jobId);
        ConfigMap configMap = kubernetesService.getConfigMap(projectId, jobId);
        try {
            kubernetesService.getConfigMap(projectId, configMap.getMetadata().getName() +
                    Constants.JOB_CONFIG_SUFFIX);
        } catch (ResourceNotFoundException e) {
            LOGGER.warn(
                    "Config map for '{}' Job Config is missing: {}. Converting old-format configmap to new-format...",
                    configMap.getMetadata().getName(),
                    e.getLocalizedMessage());
            JobDto reCreatedJobDto = JobDto.builder()
                    .name(jobDto.getName())
                    .params(jobDto.getParams())
                    .definition(jobDto.getDefinition())
                    .build();
            update(jobId, projectId, reCreatedJobDto);
        }
        PodBuilder podBuilder = initializePod(projectId, jobId, configMap, interactive);
        projectService.createOrUpdateSystemSecret(projectId);
        try {
            kubernetesService.deletePod(projectId, jobId);
            kubernetesService.deletePodsByLabels(projectId, Map.of(Constants.JOB_ID_LABEL,
                    jobId,
                    Constants.SPARK_ROLE_LABEL,
                    Constants.SPARK_ROLE_EXEC,
                    Constants.PIPELINE_JOB_ID_LABEL,
                    Constants.NOT_PIPELINE_FLAG));
        } catch (ResourceNotFoundException e) {
            LOGGER.warn(KubernetesService.NO_POD_MESSAGE, e);
        }
        podService.trackPodEvents(projectId, jobId);
        Pod created = kubernetesService.createPod(projectId, podBuilder);
        String uid = created.getMetadata().getUid();
        if (interactive) {
            jobSessionService.createSession(uid, jobDto.getDefinition());
        }
        return uid;
    }

    /**
     * Stop job.
     *
     * @param projectId project id
     * @param id        job id
     */
    public void stop(final String projectId, final String id) {
        PodStatus podStatus = kubernetesService.getPodStatus(projectId, id);
        LOGGER.info("{} POD status is {}", id, podStatus.getPhase());
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
     * @param projectId project id
     * @param jobId     job id
     * @param configMap config map
     * @return pod
     */
    private PodBuilder initializePod(String projectId, String jobId, ConfigMap configMap, boolean interactive) {
        String userName = authenticationService.getUserInfo().map(UserInfo::getUsername).orElseThrow();
        return new PodBuilder()
                .withNewMetadata()
                .withName(jobId)
                .withNamespace(projectId)
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToLabels(Constants.JOB_ID_LABEL, jobId)
                .addToLabels(Constants.STARTED_BY, userName)
                .endMetadata()
                .withNewSpec()
                .withServiceAccountName(appProperties.getJob().getSpark().getServiceAccount())
                .withNewSecurityContext()
                .withRunAsGroup(K8sUtils.GROUP_ID)
                .withRunAsUser(K8sUtils.USER_ID)
                .withFsGroup(K8sUtils.FS_GROUP_ID)
                .endSecurityContext()
                .addNewContainer()
                .withName(K8sUtils.JOB_CONTAINER)
                .withEnv(createEnvVars(jobId, interactive))
                .withEnvFrom(new EnvFromSourceBuilder()
                                .withNewConfigMapRef()
                                .withName(jobId)
                                .endConfigMapRef()
                                .build(),
                        new EnvFromSourceBuilder()
                                .withNewSecretRef()
                                .withName(ParamUtils.SECRET_NAME)
                                .endSecretRef()
                                .build(),
                        new EnvFromSourceBuilder()
                                .withNewSecretRef()
                                .withName(SYSTEM_SECRET)
                                .endSecretRef()
                                .build())
                .withResources(K8sUtils.getResourceRequirements(configMap.getData()))
                .withCommand("/opt/spark/work-dir/entrypoint.sh")
                .withImage(appProperties.getJob().getSpark().getImage())
                .withImagePullPolicy("Always")
                .addNewVolumeMount()
                .withName("spark-pvc-volume")
                .withMountPath(appProperties.getPvc().getMountPath())
                .endVolumeMount()
                .addNewVolumeMount()
                .withName("job-config-data")
                .withMountPath(appProperties.getJob().getConfig().getMountPath())
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
                .addNewImagePullSecret(appProperties.getJob().getImagePullSecret())
                .withRestartPolicy("Never")
                .endSpec();
    }

    @NotNull
    private List<EnvVar> createEnvVars(String jobId, boolean interactive) {
        ImmutableList.Builder<EnvVar> envVars = ImmutableList.<EnvVar>builder().add(
                new EnvVarBuilder().withName("POD_UID")
                        .withNewValueFrom()
                        .withNewFieldRef()
                        .withFieldPath("metadata.uid")
                        .endFieldRef()
                        .endValueFrom()
                        .build(),
                new EnvVarBuilder()
                        .withName("POD_IP")
                        .withNewValueFrom()
                        .editOrNewFieldRef()
                        .withApiVersion("v1")
                        .withFieldPath("status.podIP")
                        .endFieldRef()
                        .endValueFrom()
                        .build(),
                new EnvVarBuilder().withName("IMAGE_PULL_SECRETS").withValue(
                        appProperties.getJob().getImagePullSecret()
                ).build(),
                new EnvVarBuilder().withName("JOB_MASTER").withValue(
                        appProperties.getJob().getSpark().getMaster()
                ).build(),
                new EnvVarBuilder().withName("POD_NAME").withValue(jobId).build(),
                new EnvVarBuilder().withName("PVC_NAME").withValue(K8sUtils.PVC_NAME).build(),
                new EnvVarBuilder().withName("MOUNT_PATH").withValue(
                        appProperties.getPvc().getMountPath()
                ).build(),
                new EnvVarBuilder().withName("BACKEND_HOST").withValue(appProperties.getServer().getHost()
                        + appProperties.getServer().getServlet().getContextPath()).build(),
                new EnvVarBuilder().withName("JOB_ID").withValue(jobId).build(),
                new EnvVarBuilder()
                        .withName("PIPELINE_JOB_ID")
                        .withValue(NOT_PIPELINE_FLAG)
                        .build(),
                new EnvVarBuilder().withName("JOB_IMAGE").withValue(
                        appProperties.getJob().getSpark().getImage()
                ).build()
        );
        if (interactive) {
            envVars.add(
                    new EnvVarBuilder().withName(VISUAL_FLOW_RUNTIME_MODE).withValue(INTERACTIVE).build()
            );
        }
        return envVars.build();
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
        for (JobOverviewDto jobOverviewDto : allJobs) {
            JobDto jobDto = getById(projectId, jobOverviewDto.getId());
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

    /**
     * Creating job data cm.
     *
     * @param jobId jobId
     * @return new job data cm
     */
    public ConfigMap toJobDataConfigMap(String jobId, @Valid JobDto jobDto,
                                        Map<String, List<DataSource>> sourcesForDemo) {
        GraphDto graphDto = GraphUtils.parseGraph(jobDto.getDefinition());
        validatorService.validateGraph(graphDto, sourcesForDemo);

        return new ConfigMapBuilder()
                .addToData(K8sUtils.createConfigMapData(graphDto))
                .withNewMetadata()
                .withName(jobId + Constants.JOB_CONFIG_SUFFIX)
                .addToLabels(Constants.PARENT, jobDto.getName())
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB_CONFIG)
                .addToAnnotations(Constants.LAST_MODIFIED, ZonedDateTime.now().format(Constants.DATE_TIME_FORMATTER))
                .endMetadata()
                .build();
    }

    /**
     * Creating job definition cm.
     *
     * @param jobId jobId
     * @return new job def cm
     */
    public ConfigMap toJobDefConfigMap(@Valid JobDto jobDto, String jobId) {
        return new ConfigMapBuilder()
                .addToData(Map.of(Constants.DEFINITION,
                        Base64.encodeBase64String(jobDto.getDefinition().toString().getBytes(StandardCharsets.UTF_8))))
                .withNewMetadata()
                .withName(jobId + Constants.JOB_DEF_SUFFIX)
                .addToLabels(Constants.PARENT, jobDto.getName())
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB_DEF)
                .addToAnnotations(Constants.LAST_MODIFIED, ZonedDateTime.now().format(Constants.DATE_TIME_FORMATTER))
                .endMetadata()
                .build();
    }

    /**
     * Copies job.
     *
     * @param projectId project id
     * @param jobId     job id
     */
    public void copy(final String projectId, final String jobId) {
        ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, jobId);
        K8sUtils.copyEntity(projectId,
                configMap,
                (String projId, ConfigMap confMap) -> {
                    String jobPrevData = confMap.getData().get(JOB_CONFIG_FIELD);
                    String jobPrevDef = confMap.getMetadata().getAnnotations().get(DEFINITION);
                    confMap.getData().remove(JOB_CONFIG_FIELD);
                    confMap.getData().remove(DEPENDENT_PIPELINE_IDS);
                    confMap.getMetadata().getAnnotations().remove(DEFINITION);
                    confMap.getData().putIfAbsent(JOB_CONFIG_PATH_FIELD, jobConfigFullPath);
                    String newJobId = createFromConfigMap(projId, confMap, false);

                    ConfigMap jobDefCm = new ConfigMapBuilder()
                            .addToData(extractJobData(projId,
                                    jobId.concat(JOB_DEF_SUFFIX),
                                    DEFINITION,
                                    jobPrevDef))
                            .withNewMetadata()
                            .addToLabels(PARENT, confMap.getMetadata().getLabels().get(NAME))
                            .withName(newJobId + JOB_DEF_SUFFIX)
                            .addToLabels(TYPE, TYPE_JOB_DEF)
                            .addToAnnotations(LAST_MODIFIED,
                                    ZonedDateTime.now().format(DATE_TIME_FORMATTER))
                            .endMetadata()
                            .build();
                    createFromConfigMap(projId, jobDefCm, false);

                    ConfigMap jobDataCm = new ConfigMapBuilder()
                            .addToData(extractJobData(projId,
                                    jobId.concat(JOB_CONFIG_SUFFIX),
                                    JOB_CONFIG_FIELD,
                                    jobPrevData))
                            .withNewMetadata()
                            .addToLabels(PARENT, confMap.getMetadata().getLabels().get(NAME))
                            .withName(newJobId + JOB_CONFIG_SUFFIX)
                            .addToLabels(TYPE, TYPE_JOB_CONFIG)
                            .addToAnnotations(LAST_MODIFIED,
                                    ZonedDateTime.now().format(DATE_TIME_FORMATTER))
                            .endMetadata()
                            .build();
                    createFromConfigMap(projId, jobDataCm, false);

                    projectService.checkConnectionDependencies(projId, confMap.getMetadata().getName(),
                            null, DependencyHandlerService.getJobDefinition(projId, confMap,
                                    argoKubernetesService));
                },
                argoKubernetesService.getAllConfigMaps(projectId));
    }

    private Map<String, String> extractJobData(String projectId, String confName,
                                               String dataField, String putIfNotExists) {
        Map<String, String> jobData = new HashMap<>();
        try {
            ConfigMap dataMap = argoKubernetesService.getConfigMap(projectId, confName);
            jobData = dataMap.getData();
        } catch (ResourceNotFoundException e) {
            LOGGER.warn("Job configmap {} has not been found: {}. Try to find param in the parent configmap.",
                    confName,
                    e.getLocalizedMessage());
            jobData.put(dataField, putIfNotExists);
        }
        return jobData;
    }

    void updateParams(String id, String projectId, JsonNode definition) {

        JsonNode jsonNode = JobUtils.parseJobDefinition(definition);

        List<String> foundParams = ProjectService.findParamsKeysInString(jsonNode.toString());
        List<ParamDto> existingParams = projectService.getParams(projectId).getParams();
        for (ParamDto param : existingParams) {
            if (foundParams.contains(param.getKey())) {
                param.getValue().getJobUsages().add(id);
            } else {
                param.getValue().getJobUsages().remove(id);
            }
        }
        projectService.updateParamsWithoutProcessing(projectId, existingParams);
    }

    public void updateDefinition(String jobId, String projectId, JsonNode source) {
        JobDto jobDto = getById(projectId, jobId);
        JsonNode definition = jobDto.getDefinition();
        ArrayNode graph = definition.withArray("graph");
        ArrayNode nodes = source.withArray("nodes");
        Map<String, JsonNode> valuesById = GraphUtils.groupNodes(nodes);
        for (JsonNode node : graph) {
            JsonNode isVertex = node.get("vertex");
            if (isVertex != null && isVertex.asBoolean()) {
                String id = node.get("id").asText();
                JsonNode value = valuesById.get(id);
                if (value != null) {
                    ((ObjectNode) node).set("value", value);
                }
            }
        }
        jobDto.setDefinition(definition);
        updateConfigMaps(jobId, projectId, jobDto);
        updateParams(jobId, projectId, definition);
    }
}
