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
package by.iba.vfapi.services.utils;

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.dto.jobs.PipelineJobOverviewDto;
import by.iba.vfapi.model.JobParams;
import by.iba.vfapi.services.DateTimeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static by.iba.vfapi.services.DependencyHandlerService.checkIfJobDependsExist;

@UtilityClass
public class JobUtils {

    /**
     * Create JobResponseDtoBuilder from configmap.
     *
     * @param configMap configmap
     * @return JobResponseDtoBuilder
     */
    public static JobDto.JobDtoBuilder convertConfigMapToJobResponse(ConfigMap configMap) {
        ObjectMeta metadata = configMap.getMetadata();
        Map<String, String> annotations = metadata.getAnnotations();
        Map<String, String> data = new HashMap<>(configMap.getData());
        data.remove(Constants.JOB_CONFIG_FIELD);
        String driverMemory = data.get(Constants.DRIVER_MEMORY);
        String executorMemory = data.get(Constants.EXECUTOR_MEMORY);
        data.replace(Constants.DRIVER_MEMORY, driverMemory.substring(0, driverMemory.length() - 1));
        data.replace(Constants.EXECUTOR_MEMORY, executorMemory.substring(0, executorMemory.length() - 1));
        return JobDto
                .builder()
                .name(metadata.getLabels().get(Constants.NAME))
                .lastModified(annotations.get(Constants.LAST_MODIFIED))
                .params(getJobParams(data));
    }

    /**
     * Convert Map<String, String> to JobParams object.
     *
     * @param params map params
     * @return JobParams
     */
    public static JobParams getJobParams(Map<String, String> params) {
        return new JobParams()
                .driverCores(params.get(Constants.DRIVER_CORES))
                .driverMemory(params.get(Constants.DRIVER_MEMORY))
                .driverRequestCores(params.get(Constants.DRIVER_REQUEST_CORES))
                .executorCores(params.get(Constants.EXECUTOR_CORES))
                .executorInstances(params.get(Constants.EXECUTOR_INSTANCES))
                .executorMemory(params.get(Constants.EXECUTOR_MEMORY))
                .executorRequestCores(params.get(Constants.EXECUTOR_REQUEST_CORES))
                .shufflePartitions(params.get(Constants.SHUFFLE_PARTITIONS))
                .tags(checkIfTagsExist(params)
                );
    }

    /**
     * Getting tags if they exist in config map.
     *
     * @param data config map data
     * @return List of tags or empty
     */
    public static List<String> checkIfTagsExist(Map<String, String> data) {
        if (data.get(Constants.TAGS) != null && !data.get(Constants.TAGS).isEmpty()) {
            return Arrays.asList(data.get(Constants.TAGS).split(","));
        }
        return Collections.emptyList();
    }

    public static JobOverviewDto.JobOverviewDtoBuilder convertConfigMapToJobOverview(ConfigMap configMap) {
        return JobOverviewDto
                .builder()
                .id(configMap.getMetadata().getName())
                .name(configMap.getMetadata().getLabels().get(Constants.NAME))
                .tags(checkIfTagsExist(configMap.getData()))
                .dependentPipelineIds(checkIfJobDependsExist(configMap.getData()))
                .lastModified(configMap.getMetadata().getAnnotations().get(Constants.LAST_MODIFIED));
    }

    public static JobOverviewListDto withPipelineJobs(JobOverviewListDto jobOverviewDtos) {
        List<JobOverviewDto> jobs = new ArrayList<>();
        for (JobOverviewDto job : jobOverviewDtos.getJobs()) {
            jobs.add(job);
            jobs.addAll(job
                    .getPipelineInstances()
                    .stream()
                    .map(pipelineJob -> JobOverviewDto
                            .builder()
                            .id(pipelineJob.getId())
                            .name(job.getName())
                            .startedAt(pipelineJob.getStartedAt())
                            .finishedAt(pipelineJob.getFinishedAt())
                            .status(pipelineJob.getStatus())
                            .lastModified(job.getLastModified())
                            .pipelineId(pipelineJob.getPipelineId())
                            .usage(pipelineJob.getUsage())
                            .tags(job.getTags())
                            .dependentPipelineIds(job.getDependentPipelineIds())
                            .build())
                    .collect(Collectors.toList()));
        }

        return JobOverviewListDto.builder().jobs(jobs).editable(jobOverviewDtos.isEditable()).build();
    }

    /**
     * Create PipelineJobOverviewDtoBuilder from pod.
     *
     * @param pod pod
     * @return PipelineJobOverviewDtoBuilder
     */
    public static PipelineJobOverviewDto.PipelineJobOverviewDtoBuilder convertPodToJob(Pod pod) {
        return PipelineJobOverviewDto
                .builder()
                .id(pod.getMetadata().getName())
                .pipelineId(pod.getMetadata().getLabels().get(Constants.PIPELINE_ID_LABEL))
                .startedAt(DateTimeUtils.getFormattedDateTime(pod.getStatus().getStartTime()))
                .finishedAt(DateTimeUtils.getFormattedDateTime(K8sUtils.extractTerminatedStateField(
                        pod.getStatus(),
                        ContainerStateTerminated::getFinishedAt)))
                .status(pod.getStatus().getPhase());
    }

    /**
     * Clean up job definition. To delete unnecessary fields for example.
     *
     * @param jobDefinition job definition as JsonNode
     * @return jobDefinition
     */
    public static JsonNode parseJobDefinition(JsonNode jobDefinition) {
        for (JsonNode values : jobDefinition.get(Constants.GRAPH_LABEL)) {
            Optional<ObjectNode> optValue = Optional.ofNullable((ObjectNode) values.get(Constants.VALUE_LABEL));
            optValue.ifPresent(jsonNodes -> jsonNodes.remove(Constants.DEPENDENT_JOB_IDS));
        }
        return jobDefinition;
    }
}
