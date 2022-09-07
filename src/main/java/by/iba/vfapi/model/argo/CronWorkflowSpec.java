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

package by.iba.vfapi.model.argo;

import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import java.io.Serializable;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Class represents spec for custom resource Workflow.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class CronWorkflowSpec implements Serializable {
    private static final long serialVersionUID = 1;

    private String schedule;
    private String concurrencyPolicy;
    private int successfulJobsHistoryLimit;
    private int failedJobsHistoryLimit;
    private boolean suspend;
    private WorkflowSpec workflowSpec;

    public static CronWorkflowSpec fromDtoAndWFTMPLName(
        @Valid CronPipelineDto cronPipelineDto, String workflowTemplateName) {
        return new CronWorkflowSpec(
            cronPipelineDto.getSchedule(),
            "Forbid",
            1,
            1,
            cronPipelineDto.isSuspend(),
            new WorkflowSpec().workflowTemplateRef(new WorkflowTemplateRef().name(workflowTemplateName)));
    }
}
