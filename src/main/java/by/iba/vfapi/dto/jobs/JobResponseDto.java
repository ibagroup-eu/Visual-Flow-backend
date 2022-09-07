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
import by.iba.vfapi.exceptions.InternalProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.codec.binary.Base64;

/**
 * Single job response DTO class.
 */
@EqualsAndHashCode
@Builder
@Getter
@ToString
public class JobResponseDto {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @Schema(description = "Job's name", example = "test_Job1")
    private final String name;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_DEFINITION)
    private final JsonNode definition;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private final String startedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_SECOND)
    private final String finishedAt;
    @Schema(ref = OpenApiConfig.SCHEMA_DATETIME_FIRST)
    private final String lastModified;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_PARAMS)
    private final Map<String, String> params;
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_STATUS)
    private final String status;
    @Schema(description = "Whether current user can run the job and whether the job has some stages in it")
    private final boolean runnable;
    @Schema(description = "Whether current user can edit the job.")
    private final boolean editable;

    /**
     * Create JobResponseDtoBuilder from configmap.
     *
     * @param configMap configmap
     * @return JobResponseDtoBuilder
     */
    public static JobResponseDtoBuilder fromConfigMap(ConfigMap configMap) {
        ObjectMeta metadata = configMap.getMetadata();
        Map<String, String> annotations = metadata.getAnnotations();
        Map<String, String> data = new HashMap<>(configMap.getData());
        data.remove(Constants.JOB_CONFIG_FIELD);
        String driverMemory = data.get(Constants.DRIVER_MEMORY);
        String executorMemory = data.get(Constants.EXECUTOR_MEMORY);
        data.replace(Constants.DRIVER_MEMORY, driverMemory.substring(0, driverMemory.length() - 1));
        data.replace(Constants.EXECUTOR_MEMORY, executorMemory.substring(0, executorMemory.length() - 1));
        try {
            return JobResponseDto
                .builder()
                .name(metadata.getLabels().get(Constants.NAME))
                .definition(MAPPER.readTree(Base64.decodeBase64(annotations.get(Constants.DEFINITION))))
                .lastModified(annotations.get(Constants.LAST_MODIFIED))
                .params(data);
        } catch (IOException e) {
            throw new InternalProcessingException("Unable to parse definition JSON", e);
        }
    }
}
