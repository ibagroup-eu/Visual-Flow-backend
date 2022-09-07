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

package by.iba.vfapi.dto.jobs;

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.ResourceUsageDto;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * job overview DTO class.
 */
@Builder
@EqualsAndHashCode
@Getter
@ToString
@Schema(description = "DTO with essential info about the job")
public class JobOverviewDto {
    @Schema(ref = OpenApiConfig.SCHEMA_UUID_ONE)
    private final String id;
    @Schema(description = "Job's name", example = "test_Job1")
    private final String name;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private final String startedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_SECOND)
    private final String finishedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_STATUS)
    private final String status;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private final String lastModified;
    private final ResourceUsageDto usage;
    private final List<PipelineJobOverviewDto> pipelineInstances;
    //TODO remove field pipelineId
    private final String pipelineId;
    @Schema(description = "Whether current user can run the job and whether the job has some stages in it")
    private final boolean runnable;

    public static JobOverviewDto.JobOverviewDtoBuilder fromConfigMap(ConfigMap configMap) {
        return JobOverviewDto
            .builder()
            .id(configMap.getMetadata().getName())
            .name(configMap.getMetadata().getLabels().get(Constants.NAME))
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
                                .build())
                            .collect(Collectors.toList()));
        }

        return JobOverviewListDto.builder().jobs(jobs).editable(jobOverviewDtos.isEditable()).build();
    }
}
