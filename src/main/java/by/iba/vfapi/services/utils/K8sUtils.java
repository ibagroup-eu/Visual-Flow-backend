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
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.JobParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static by.iba.vfapi.dto.Constants.NAME;

/**
 * Util class for Kubernetes service.
 */
@UtilityClass
@Slf4j
public class K8sUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final Pattern NON_WORD_PATTERN = Pattern.compile("[^A-Za-z0-9]+");
    public static final String APP = "app";
    public static final String JOB_CONTAINER = "main";
    public static final String APPLICATION_ROLE = "vf-role";
    public static final Pattern LOG_PATTERN =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2},\\d{3})\\s(?:\\[.+?\\s){2}(\\w+)\\s+(.+)$");
    public static final String DRAFT_STATUS = "Draft";
    public static final String SUSPENDED_STATUS = "Suspended";
    public static final String TERMINATED_STATUS = "Terminated";
    public static final String SUCCEEDED_STATUS = "Succeeded";
    public static final String STOPPED_STATUS = "Stopped";
    public static final String RUNNING_STATUS = "Running";
    public static final String PENDING_STATUS = "Pending";
    public static final String ERROR_STATUS = "Error";
    public static final String FAILED_STATUS = "Failed";
    public static final String SHUTDOWN_TERMINATE = "Terminate";
    public static final String SHUTDOWN_STOP = "Stop";
    public static final String PVC_POD_NAME = "vf-k8s-pvc";
    public static final String PVC_POD_IMAGE = "busybox:stable";
    public static final String PVC_NAME = "vf-pvc";
    public static final String PVC_VOLUME_NAME = "vf-pvc-volume";
    public static final long USER_ID = 1000L;
    public static final long GROUP_ID = 1000L;
    public static final long FS_GROUP_ID = 1000L;

    public static final String CONFIGMAP = "configMap";

    public static String extractTerminatedStateField(
            PodStatus podStatus, Function<ContainerStateTerminated, String> method) {
        return podStatus
                .getContainerStatuses()
                .stream()
                .map((ContainerStatus cs) -> cs.getState().getTerminated())
                .filter(Objects::nonNull)
                .map(method)
                .filter(Objects::nonNull)
                .max(Comparator.nullsFirst(Comparator.comparing(ZonedDateTime::parse, ZonedDateTime::compareTo)))
                .orElse(null);
    }

    /**
     * Getting valid K8s name.
     *
     * @param name invalid name
     * @return valid name
     */
    public static String getValidK8sName(final String name) {
        return NON_WORD_PATTERN.matcher(name).replaceAll("-").toLowerCase(Locale.getDefault());
    }

    /**
     * Getting resourceRequirements for Container.
     *
     * @return resourceRequirements
     */
    public static ResourceRequirements getResourceRequirements(Map<String, String> params) {
        Quantity limitMem = Quantity.parse(Quantity
                .getAmountInBytes(Quantity.parse(params.get(Constants.DRIVER_MEMORY)))
                .multiply(BigDecimal.valueOf(Constants.MEMORY_OVERHEAD_FACTOR))
                .toBigInteger()
                .toString());
        return new ResourceRequirementsBuilder()
                .addToLimits(Constants.CPU_FIELD, Quantity.parse(params.get(Constants.DRIVER_CORES)))
                .addToLimits(Constants.MEMORY_FIELD, limitMem)
                .addToRequests(Constants.CPU_FIELD, Quantity.parse(params.get(Constants.DRIVER_REQUEST_CORES)))
                .addToRequests(Constants.MEMORY_FIELD, Quantity.parse(params.get(Constants.DRIVER_MEMORY)))
                .build();
    }

    /**
     * Generate kubernetes compatible UUID where the first symbol is a guaranteed alphabetic character.
     *
     * @return UUID in string format
     */
    public static String getKubeCompatibleUUID() {
        return RandomStringUtils.random(1, 0, 0, true, false, null, new SecureRandom())
                + UUID.randomUUID().toString().substring(1);
    }

    /**
     * Helper method to generate unique name for k8s entity
     *
     * @param nameValidator supposed to check whether entity with such name already exists in k8s
     * @return unique name
     */
    public static String getUniqueEntityName(
            Function<String, Object> nameValidator) {
        String name = UUID.randomUUID().toString();
        while (true) {
            try {
                Object apply = nameValidator.apply(name);
                if (apply == null) {
                    return name;
                }
                name = UUID.randomUUID().toString();
            } catch (ResourceNotFoundException e) {
                LOGGER.warn("Unique entity name has not been found! {}", e.getLocalizedMessage());
                break;
            }
        }
        return name;
    }

    /**
     * Creating job cm.
     *
     * @param id job id
     * @return new cm
     */
    public static ConfigMap toConfigMap(String id, JobDto jobDto, String jobDataFilePath) {
        Map<String, String> configMapData = new HashMap<>(toMap(jobDto.getParams()));
        configMapData.put(Constants.JOB_CONFIG_PATH_FIELD, jobDataFilePath);

        configMapData.replace(Constants.EXECUTOR_MEMORY,
                configMapData.get(Constants.EXECUTOR_MEMORY) + Constants.GIGABYTE_QUANTITY);
        configMapData.replace(Constants.DRIVER_MEMORY,
                configMapData.get(Constants.DRIVER_MEMORY) + Constants.GIGABYTE_QUANTITY);

        return new ConfigMapBuilder()
                .addToData(configMapData)
                .withNewMetadata()
                .withName(id)
                .addToLabels(Constants.NAME, jobDto.getName())
                .addToLabels(Constants.TYPE, Constants.TYPE_JOB)
                .addToAnnotations(Constants.LAST_MODIFIED, ZonedDateTime.now().format(Constants.DATE_TIME_FORMATTER))
                .endMetadata()
                .build();
    }

    /**
     * Convert JobParams object to Map<String, String>.
     *
     * @param jobParams job params
     */
    private static Map<String, String> toMap(JobParams jobParams) {
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

    /**
     * Creating data for configMap.
     *
     * @return data Map(String, String)
     */
    public static Map<String, String> createConfigMapData(GraphDto graphDto) {
        try {
            return Map.of(Constants.JOB_CONFIG_FIELD, MAPPER.writeValueAsString(graphDto));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Bad graph structure", e);
        }
    }

    /**
     * Creates a copy of existing entity
     *
     * @param projectId          id of the project
     * @param entityMetadata     entity's metadata
     * @param saver              lambda for saving the copy
     * @param entityMetadataList list of all available entities(of the same type) from current project
     * @param <T>                entity's metadata
     */
    public static <T extends HasMetadata> void copyEntity(
            final String projectId, T entityMetadata, BiConsumer<String, T> saver, List<T> entityMetadataList) {
        String currentName = entityMetadata.getMetadata().getLabels().get(NAME);
        int availableIndex = getNextEntityCopyIndex(currentName, entityMetadataList);
        if (availableIndex == 1) {
            entityMetadata.getMetadata().getLabels().replace(NAME, currentName,
                    currentName + "-Copy");
        } else {
            entityMetadata
                    .getMetadata()
                    .getLabels()
                    .replace(NAME, currentName, currentName + "-Copy" + availableIndex);

        }
        String newId = UUID.randomUUID().toString();
        entityMetadata.getMetadata().setName(newId);
        saver.accept(projectId, entityMetadata);
    }

    /**
     * Returns the next available index
     *
     * @param entityName         name of the entity
     * @param entityMetadataList list of all entities
     * @param <T>                entity type
     * @return number of copies
     */
    public static <T extends HasMetadata> int getNextEntityCopyIndex(String entityName,
                                                                     Collection<T> entityMetadataList) {
        Pattern groupIndex = Pattern.compile(String.format("^%s-Copy(\\d+)?$", Pattern.quote(entityName)));
        return entityMetadataList.stream().map((T e) -> {
            String name = e.getMetadata().getLabels().get(NAME);
            Matcher matcher = groupIndex.matcher(name);
            if (!matcher.matches()) {
                return 0;
            }
            return Optional.ofNullable(matcher.group(1)).map(Integer::valueOf).orElse(1);
        }).max(Comparator.naturalOrder()).map(index -> index + 1).orElse(1);

    }
}
