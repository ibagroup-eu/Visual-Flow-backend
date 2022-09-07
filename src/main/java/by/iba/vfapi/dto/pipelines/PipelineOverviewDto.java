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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Pipeline response DTO class.
 */
@EqualsAndHashCode
@NoArgsConstructor
@Getter
@ToString
@Schema(description = "DTO with basic info about the pipeline")
public class PipelineOverviewDto {
    @Schema(ref = OpenApiConfig.SCHEMA_UUID_TWO)
    private String id;
    @Schema(description = "Pipeline's name", example = "test_pipe321")
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
    @Schema(description = "Whether current user can run the pipeline")
    private boolean runnable;
    @Schema(ref = OpenApiConfig.SCHEMA_PIPELINE_STAGE_STATUSES)
    private Map<String, String> jobsStatuses;

    /**
     * Setter for id.
     *
     * @param id id
     * @return this
     */
    public PipelineOverviewDto id(String id) {
        this.id = id;
        return this;
    }

    /**
     * Setter for name.
     *
     * @param name name
     * @return this
     */
    public PipelineOverviewDto name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Setter for lastModified.
     *
     * @param lastModified lastModified
     * @return this
     */
    public PipelineOverviewDto lastModified(String lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    /**
     * Setter for startedAt.
     *
     * @param startedAt startedAt
     * @return this
     */
    public PipelineOverviewDto startedAt(String startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    /**
     * Setter for finishedAt.
     *
     * @param finishedAt finishedAt
     * @return this
     */
    public PipelineOverviewDto finishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    /**
     * Setter for status.
     *
     * @param status status
     * @return this
     */
    public PipelineOverviewDto status(String status) {
        this.status = status;
        return this;
    }

    /**
     * Setter for progress.
     *
     * @param progress progress
     * @return this
     */
    public PipelineOverviewDto progress(double progress) {
        this.progress = progress;
        return this;
    }

    /**
     * Setter for cron.
     *
     * @param cron cron
     * @return this
     */
    public PipelineOverviewDto cron(boolean cron) {
        this.cron = cron;
        return this;
    }

    /**
     * Setter for runnable.
     *
     * @param runnable runnable
     * @return this
     */
    public PipelineOverviewDto runnable(boolean runnable) {
        this.runnable = runnable;
        return this;
    }

    /**
     * Setter for jobsStatuses.
     *
     * @param jobsStatuses jobsStatuses
     * @return this
     */
    public PipelineOverviewDto jobsStatuses(Map<String, String> jobsStatuses) {
        this.jobsStatuses = jobsStatuses;
        return this;
    }

}
