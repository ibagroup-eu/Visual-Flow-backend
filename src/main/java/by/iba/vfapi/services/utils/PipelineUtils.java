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

import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import by.iba.vfapi.model.argo.CronWorkflowSpec;
import lombok.experimental.UtilityClass;

/**
 * Utility class for methods, connected with pipelines management.
 */
@UtilityClass
public class PipelineUtils {

    /**
     * Utility method for converting {@link CronWorkflowSpec} into {@link CronPipelineDto} object.
     *
     * @param cronWorkflowSpec cron workflow spec
     * @return converted cron pipeline.
     */
    public static CronPipelineDto convertSpecToWorkflow(CronWorkflowSpec cronWorkflowSpec) {
        return new CronPipelineDto(cronWorkflowSpec.getSchedule(), cronWorkflowSpec.isSuspend());
    }
}
