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

package by.iba.vfapi.dto.pipelines;

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.dto.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline response DTO class.
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "DTO with basic info about the pipeline")
public class PipelineOverviewDto {
    @Schema(ref = OpenApiConfig.SCHEMA_UUID_TWO)
    private String id;
    @Pattern(regexp = Constants.NAME_PATTERN, message = "The name should only consist of alphanumerics or ' " +
            "\\-_' characters and have a total length between 3 and 40 characters max")
    @Schema(description = "Name of the pipeline", example = "test_pipe321")
    private String name;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private String lastModified;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private String startedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_SECOND)
    private String finishedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_PIPELINE_STATUS)
    private String status;
    @Schema(description = "Pipeline's completion progress")
    private double progress;
    @Schema(description = "Whether pipeline is represented by scheduled workflow")
    private boolean cron;
    @Schema(description = "If true Workflow scheduling will not occur")
    private boolean cronSuspend;
    @Schema(description = "Whether current user can run the pipeline")
    private boolean runnable;
    @Schema(description = "Pipeline's list of tags")
    private List<String> tags;
    @Schema(description = "Pipeline's list of dependencies")
    private Set<String> dependentPipelineIds;
    @Schema(ref = OpenApiConfig.SCHEMA_PIPELINE_STAGE_STATUSES)
    private Map<String, String> jobsStatuses;

    /**
     * Setter for tags.
     *
     * @param tags tags
     * @return this
     */
    public PipelineOverviewDto tags(Collection<String> tags) {
        if (tags != null) {
            this.tags = new ArrayList<>(tags);
        }
        return this;
    }

    /**
     * Setter for dependentPipelineIds.
     *
     * @param  dependentPipelineIds dependentPipelineIds
     * @return this
     */
    public PipelineOverviewDto dependentPipelineIds(Collection<String> dependentPipelineIds) {
        if (dependentPipelineIds != null) {
            this.dependentPipelineIds = new HashSet<>(dependentPipelineIds);
        }
        return this;
    }
}
