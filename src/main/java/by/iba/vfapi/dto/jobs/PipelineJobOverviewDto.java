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
import by.iba.vfapi.services.DateTimeUtils;
import by.iba.vfapi.services.K8sUtils;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.Pod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * job pipeline overview DTO class.
 */
@Builder
@EqualsAndHashCode
@Getter
@ToString
@Schema(description = "DTO that represents job instance that is bound to some pipeline")
public class PipelineJobOverviewDto {
    @Schema(ref = OpenApiConfig.SCHEMA_INSTANCE_UUID_ONE)
    private final String id;
    @Schema(ref = OpenApiConfig.SCHEMA_UUID_ONE)
    private final String pipelineId;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private final String startedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private final String finishedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_STATUS)
    private final String status;
    private final ResourceUsageDto usage;

    /**
     * Create PipelineJobOverviewDtoBuilder from pod.
     *
     * @param pod pod
     * @return PipelineJobOverviewDtoBuilder
     */
    public static PipelineJobOverviewDto.PipelineJobOverviewDtoBuilder fromPod(Pod pod) {
        return PipelineJobOverviewDto
            .builder()
            .id(pod.getMetadata().getName())
            .pipelineId(pod.getMetadata().getLabels().get(Constants.WORKFLOW_POD_LABEL))
            .startedAt(DateTimeUtils.getFormattedDateTime(pod.getStatus().getStartTime()))
            .finishedAt(DateTimeUtils.getFormattedDateTime(K8sUtils.extractTerminatedStateField(
                pod.getStatus(),
                ContainerStateTerminated::getFinishedAt)))
            .status(pod.getStatus().getPhase());
    }
}
