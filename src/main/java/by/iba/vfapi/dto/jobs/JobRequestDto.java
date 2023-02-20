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
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.JobParams;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.codec.binary.Base64;

/**
 * Job DTO class.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Schema(description = "DTO with job's information, including it's definition and graph")
public class JobRequestDto {
    private static final int TWO = 2;
    private static final String OPERATION_FIELD = "operation";
    @NotNull
    @Schema(description = "Job's name", example = "test_Job1")
    private String name;
    @NotNull
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_DEFINITION)
    private JsonNode definition;
    @NotNull
    @Schema(ref = OpenApiConfig.SCHEMA_JOB_PARAMS)
    private JobParams params;

    /**
     * Creating job cm.
     *
     * @param id job id
     * @return new cm
     */
    public ConfigMap toConfigMap(String id) {
        GraphDto graphDto = GraphDto.parseGraph(definition);
        validateGraph(graphDto);

        Map<String, String> configMapData = new HashMap<>(graphDto.createConfigMapData());
        configMapData.putAll(toMap(params));

        configMapData.replace(Constants.EXECUTOR_MEMORY,
                              configMapData.get(Constants.EXECUTOR_MEMORY) + Constants.GIGABYTE_QUANTITY);
        configMapData.replace(Constants.DRIVER_MEMORY,
                              configMapData.get(Constants.DRIVER_MEMORY) + Constants.GIGABYTE_QUANTITY);

        return new ConfigMapBuilder()
            .addToData(configMapData)
            .withNewMetadata()
            .withName(id)
            .addToLabels(Constants.NAME, name)
            .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
            .addToAnnotations(Constants.DEFINITION,
                              Base64.encodeBase64String(definition.toString().getBytes(StandardCharsets.UTF_8)))
            .addToAnnotations(Constants.LAST_MODIFIED, ZonedDateTime.now().format(Constants.DATE_TIME_FORMATTER))
            .endMetadata()
            .build();
    }

    /**
     * Validate stages connections in jobs.
     *
     * @param graphDto graph with nodes and edges
     */
    public void validateGraph(GraphDto graphDto) {
        List<GraphDto.NodeDto> nodes = graphDto.getNodes();
        List<String> targets =
            graphDto.getEdges().stream().map(GraphDto.EdgeDto::getTarget).collect(Collectors.toList());
        List<String> sources =
            graphDto.getEdges().stream().map(GraphDto.EdgeDto::getSource).collect(Collectors.toList());
        for (GraphDto.NodeDto node : nodes) {
            long targetsCount = targets.stream().filter(target -> target.equals(node.getId())).count();
            String operation = node.getValue().get(OPERATION_FIELD);
            if ("READ".equals(operation) && targets.contains(node.getId())) {
                throw new BadRequestException(String.format("%s stage can have only output arrows", operation));
            } else if (("WRITE".equals(operation) ||
                        "VALIDATE".equals(operation)) && (sources.contains(node.getId()) || targetsCount != 1)) {
                throw new BadRequestException(String.format("%s stage must have only one input arrows",
                                                            operation));
            } else if (("UNION".equals(operation) || "JOIN".equals(operation) || "CDC".equals(operation)) &&
                targetsCount != TWO) {
                throw new BadRequestException(String.format("%s stage must have two input arrows", operation));
            } else if (("TRANSFORM".equals(operation) ||
                "GROUP".equals(operation) ||
                "FILTER".equals(operation) ||
                "REMOVE_DUPLICATES".equals(operation) ||
                "SORT".equals(operation) ||
                "CACHE".equals(operation) ||
                "SLICE".equals(operation) ||
                "WITH_COLUMN".equals(operation) ||
                "PIVOT".equals(operation) ||
                "DATETIME".equals(operation) ||
                "STRING".equals(operation) ||
                "HANDLE_NULL".equals(operation)) && targetsCount != 1) {
                throw new BadRequestException(String.format("%s stage must have one input arrows", operation));
            } else if (!List.of("READ",
                                "WRITE",
                                "UNION",
                                "JOIN",
                                "CDC",
                                "TRANSFORM",
                                "GROUP",
                                "FILTER",
                                "REMOVE_DUPLICATES",
                                "SORT",
                                "CACHE",
                                "SLICE",
                                "WITH_COLUMN",
                                "VALIDATE",
                                "PIVOT",
                                "DATETIME",
                                "STRING",
                                "HANDLE_NULL").contains(operation)) {
                throw new BadRequestException("Invalid stage type");
            }
        }
    }

    /**
     * Convert JobParams object to Map<String, String>.
     *
     * @param jobParams job params
     * @return Map<String, String> params
     */
    public Map<String, String> toMap(JobParams jobParams) {
        return Map.of(
                Constants.DRIVER_CORES, jobParams.getDriverCores(),
                Constants.DRIVER_MEMORY, jobParams.getDriverMemory(),
                Constants.DRIVER_REQUEST_CORES, jobParams.getDriverRequestCores(),
                Constants.EXECUTOR_CORES, jobParams.getExecutorCores(),
                Constants.EXECUTOR_INSTANCES, jobParams.getExecutorInstances(),
                Constants.EXECUTOR_MEMORY, jobParams.getExecutorMemory(),
                Constants.EXECUTOR_REQUEST_CORES, jobParams.getExecutorRequestCores(),
                Constants.SHUFFLE_PARTITIONS, jobParams.getShufflePartitions(),
                Constants.TAGS, String.join(",", jobParams.getTags()
        ));
    }
}
