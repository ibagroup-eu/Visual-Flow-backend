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

import by.iba.vfapi.model.argo.CronWorkflowSpec;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CRON pipeline DTO class.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Schema(description = "DTO for a scheduled pipeline")
public class CronPipelineDto {
    @NotNull
    @Schema(description = "Schedule at which the Workflow will be run", example = "5 4 * * *")
    private String schedule;
    @Schema(description = "If true Workflow scheduling will not occur")
    private boolean suspend;

    public static CronPipelineDto fromSpec(CronWorkflowSpec cronWorkflowSpec) {
        return new CronPipelineDto(cronWorkflowSpec.getSchedule(), cronWorkflowSpec.isSuspend());
    }
}
