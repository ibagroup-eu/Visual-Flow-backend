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

import by.iba.vfapi.config.ApplicationConfigurationProperties;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.DataSource;
import by.iba.vfapi.dto.projects.DemoLimitsDto;
import by.iba.vfapi.dto.projects.ProjectOverviewDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.dto.projects.ResourceQuotaRequestDto;
import by.iba.vfapi.dto.projects.ResourceQuotaResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NamespaceFluent;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Instant;

import javax.validation.Valid;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static by.iba.vfapi.dto.Constants.DEMO_FIELD;
import static by.iba.vfapi.dto.Constants.SYSTEM_SECRET;

/**
 * Utility class for methods, connected with projects management.
 */
@UtilityClass
@Slf4j
public class ProjectUtils {

    private static final String DEMO_LIMITS_NULL_ERR = "Demo limits should be defined, since '%s' project is demo.";
    private static final String SERIALIZE_DS_ERR =
            "Cannot create demo project '{}', since demo limits have incorrect format: {}";

    /**
     * Converts Namespace to Project DTO.
     * Also determines, if the project is locked (validTo is expired) for
     * demo project.
     *
     * @param namespace namespace to convert.
     * @return project dto builder.
     */
    public static ProjectOverviewDto convertNamespaceToProjectOverview(HasMetadata namespace, boolean isLocked,
                                                                       boolean isSuperUser) {
        ProjectOverviewDto.ProjectOverviewDtoBuilder projectOverviewBuilder = ProjectOverviewDto
                .builder()
                .name(namespace.getMetadata().getAnnotations().get(Constants.NAME_FIELD))
                .description(namespace.getMetadata().getAnnotations().get(Constants.DESCRIPTION_FIELD))
                .id(namespace.getMetadata().getName())
                .isLocked(isLocked);

        if (Boolean.parseBoolean(namespace.getMetadata().getAnnotations().get(DEMO_FIELD))) {
            String validToField = namespace.getMetadata().getAnnotations().get(Constants.VALID_TO_FIELD);
            projectOverviewBuilder.isLocked(isLocked ||
                    (!isSuperUser && Instant.parse(validToField).isBeforeNow()));
        }
        return projectOverviewBuilder.build();
    }

    /**
     * Converts Project DTO to namespace.
     *
     * @param id                project id.
     * @param projectDto        project DTO.
     * @param customAnnotations custom annotations.
     * @return namespace builder.
     */
    public static NamespaceBuilder convertDtoToNamespace(String id,
                                                         @Valid ProjectRequestDto projectDto,
                                                         Map<String, String> customAnnotations) {
        NamespaceFluent.MetadataNested<NamespaceBuilder> namespaceBuilderMetadataNested =
                new NamespaceBuilder().withNewMetadata().withName(id);
        if (!customAnnotations.isEmpty()) {
            namespaceBuilderMetadataNested.addToAnnotations(customAnnotations);
        }
        if (projectDto.isDemo()) {
            if (projectDto.getDemoLimits() == null) {
                throw new BadRequestException(String.format(DEMO_LIMITS_NULL_ERR, id));
            }
            try {
                DemoLimitsDto demoLimits = projectDto.getDemoLimits();
                namespaceBuilderMetadataNested
                        .addToAnnotations(Constants.VALID_TO_FIELD, demoLimits.getExpirationDate().toString())
                        .addToAnnotations(Constants.DATASOURCE_LIMIT, new ObjectMapper()
                                .writeValueAsString(demoLimits.getSourcesToShow()))
                        .addToAnnotations(Constants.JOBS_LIMIT, String.valueOf(demoLimits.getJobsNumAllowed()))
                        .addToAnnotations(Constants.PIPELINES_LIMIT,
                                String.valueOf(demoLimits.getPipelinesNumAllowed()));
            } catch (JacksonException e) {
                LOGGER.error(SERIALIZE_DS_ERR, id, e.getLocalizedMessage());
            }
        }
        return namespaceBuilderMetadataNested
                .addToAnnotations(DEMO_FIELD, String.valueOf(projectDto.isDemo()))
                .addToAnnotations(Constants.NAME_FIELD, projectDto.getName())
                .addToAnnotations(Constants.DESCRIPTION_FIELD, projectDto.getDescription())
                .endMetadata();
    }

    /**
     * Converts Namespace to Project DTO.
     *
     * @param namespace namespace to convert.
     * @return project dto builder.
     */
    public static ProjectResponseDto.ProjectResponseDtoBuilder convertNamespaceToProjectResponse(Namespace namespace,
                                                                                                 boolean demoEditable) {
        String name = namespace.getMetadata().getAnnotations().get(Constants.NAME_FIELD);
        boolean isDemo = Boolean.parseBoolean(namespace.getMetadata().getAnnotations().get(DEMO_FIELD));
        ProjectResponseDto.ProjectResponseDtoBuilder projectDto = ProjectResponseDto
                .builder()
                .name(name)
                .description(namespace.getMetadata().getAnnotations().get(Constants.DESCRIPTION_FIELD))
                .id(namespace.getMetadata().getName())
                .demo(isDemo);
        if (isDemo) {
            appendDemoLimits(namespace, name, projectDto, demoEditable);
        }
        return projectDto;
    }

    /**
     * Private method for appending demo limits.
     *
     * @param namespace  is a Kubernetes namespace.
     * @param projectId  is a project ID.
     * @param projectDto is a project DTO.
     */
    private static void appendDemoLimits(Namespace namespace, String projectId,
                                         ProjectResponseDto.ProjectResponseDtoBuilder projectDto,
                                         boolean demoEditable) {
        DemoLimitsDto.DemoLimitsDtoBuilder demoLimitsBuilder = DemoLimitsDto.builder();
        String sourcesToShowStr = namespace.getMetadata().getAnnotations().get(Constants.DATASOURCE_LIMIT);
        if (sourcesToShowStr != null) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<DataSource>> sourcesToShow = new HashMap<>();
            try {
                JsonNode sourcesToShowNode = mapper.readTree(sourcesToShowStr);
                Iterator<Map.Entry<String, JsonNode>> iterator = sourcesToShowNode.fields();
                TypeReference<List<DataSource>> dataSourceListTR = new TypeReference<>() {
                };
                while (iterator.hasNext()) {
                    Map.Entry<String, JsonNode> nextVal = iterator.next();
                    sourcesToShow.put(nextVal.getKey(), mapper.convertValue(nextVal.getValue(), dataSourceListTR));
                }
            } catch (JacksonException e) {
                LOGGER.error("Cannot get information about available data sources for '{}' project: {}", projectId,
                        e.getLocalizedMessage());
            }
            demoLimitsBuilder.sourcesToShow(sourcesToShow);
        }
        String jobsLimit = namespace.getMetadata().getAnnotations().get(Constants.JOBS_LIMIT);
        if (jobsLimit != null) {
            demoLimitsBuilder.jobsNumAllowed(Integer.parseInt(jobsLimit));
        }
        String pipelinesLimit = namespace.getMetadata().getAnnotations().get(Constants.PIPELINES_LIMIT);
        if (pipelinesLimit != null) {
            demoLimitsBuilder.pipelinesNumAllowed(Integer.parseInt(pipelinesLimit));
        }
        String validToField = namespace.getMetadata().getAnnotations().get(Constants.VALID_TO_FIELD);
        if (validToField != null) {
            LocalDate expirationDate = LocalDate.parse(validToField);
            demoLimitsBuilder.expirationDate(expirationDate);
            projectDto.locked(!demoEditable && expirationDate.isBefore(LocalDate.now()));
        }
        demoLimitsBuilder.editable(demoEditable);
        projectDto.demoLimits(demoLimitsBuilder.build());
    }

    /**
     * Creating ResourceQuotaBuilder.
     *
     * @return ResourceQuotaBuilder
     */
    public ResourceQuotaBuilder toResourceQuota(@Valid ResourceQuotaRequestDto resourceDto) {
        Quantity limitsCpuQuantity = Quantity.parse(resourceDto.getLimitsCpu().toString());
        Quantity requestsCpuQuantity = Quantity.parse(resourceDto.getRequestsCpu().toString());
        Quantity limitsMemoryQuantity = Quantity.parse(resourceDto.getLimitsMemory() +
                Constants.GIGABYTE_QUANTITY);
        Quantity requestsMemoryQuantity = Quantity.parse(resourceDto.getRequestsMemory() +
                Constants.GIGABYTE_QUANTITY);

        if (Quantity
                .getAmountInBytes(requestsCpuQuantity)
                .compareTo(Quantity.getAmountInBytes(limitsCpuQuantity)) > 0 ||
                Quantity
                        .getAmountInBytes(requestsMemoryQuantity)
                        .compareTo(Quantity.getAmountInBytes(limitsMemoryQuantity)) > 0) {
            throw new BadRequestException("Requests can't be greater than limits");
        }

        return new ResourceQuotaBuilder()
                .withNewMetadata()
                .withName(Constants.QUOTA_NAME)
                .endMetadata()
                .withNewSpec()
                .addToHard(Constants.LIMITS_CPU, limitsCpuQuantity)
                .addToHard(Constants.REQUESTS_CPU, requestsCpuQuantity)
                .addToHard(Constants.LIMITS_MEMORY, limitsMemoryQuantity)
                .addToHard(Constants.REQUESTS_MEMORY, requestsMemoryQuantity)
                .endSpec();
    }

    /**
     * Creating ResourceQuotaDtoBuilder with limits from ResourceQuota.
     *
     * @param resourceQuota ResourceQuota
     * @return ResourceQuotaDtoBuilder
     */
    public static ResourceQuotaResponseDto.ResourceQuotaResponseDtoBuilder getLimitsFromResourceQuota(
            ResourceQuota resourceQuota) {
        return ResourceQuotaResponseDto
                .builder()
                .limitsCpu(Float.valueOf(resourceQuota.getStatus().getHard().get(Constants.LIMITS_CPU).getAmount()))
                .requestsCpu(Float.valueOf(resourceQuota
                        .getStatus()
                        .getHard()
                        .get(Constants.REQUESTS_CPU)
                        .getAmount()))
                .limitsMemory(Float.valueOf(resourceQuota
                        .getStatus()
                        .getHard()
                        .get(Constants.LIMITS_MEMORY)
                        .getAmount()))
                .requestsMemory(Float.valueOf(resourceQuota
                        .getStatus()
                        .getHard()
                        .get(Constants.REQUESTS_MEMORY)
                        .getAmount()));
    }

    /**
     * Creating ResourceQuotaDtoBuilder with usage from ResourceQuota.
     *
     * @param resourceQuota ResourceQuota
     * @return ResourceQuotaDtoBuilder
     */
    public static ResourceQuotaResponseDto.ResourceQuotaResponseDtoBuilder getUsageFromResourceQuota(
            ResourceQuota resourceQuota) {
        return ResourceQuotaResponseDto
                .builder()
                .limitsCpu(Quantity
                        .getAmountInBytes(resourceQuota.getStatus().getUsed().get(Constants.LIMITS_CPU))
                        .divide(
                                Quantity.getAmountInBytes(resourceQuota
                                        .getStatus()
                                        .getHard()
                                        .get(Constants.LIMITS_CPU)),
                                Constants.USAGE_ACCURACY,
                                RoundingMode.UP)
                        .floatValue())
                .limitsMemory(Quantity
                        .getAmountInBytes(resourceQuota.getStatus().getUsed().get(Constants.LIMITS_MEMORY))
                        .divide(
                                Quantity.getAmountInBytes(resourceQuota
                                        .getStatus()
                                        .getHard()
                                        .get(Constants.LIMITS_MEMORY)),
                                Constants.USAGE_ACCURACY,
                                RoundingMode.UP)
                        .floatValue())
                .requestsCpu(Quantity
                        .getAmountInBytes(resourceQuota.getStatus().getUsed().get(Constants.REQUESTS_CPU))
                        .divide(
                                Quantity.getAmountInBytes(resourceQuota
                                        .getStatus()
                                        .getHard()
                                        .get(Constants.REQUESTS_CPU)),
                                Constants.USAGE_ACCURACY,
                                RoundingMode.UP)
                        .floatValue())
                .requestsMemory(Quantity
                        .getAmountInBytes(resourceQuota
                                .getStatus()
                                .getUsed()
                                .get(Constants.REQUESTS_MEMORY))
                        .divide(
                                Quantity.getAmountInBytes(resourceQuota
                                        .getStatus()
                                        .getHard()
                                        .get(Constants.REQUESTS_MEMORY)),
                                Constants.USAGE_ACCURACY,
                                RoundingMode.UP)
                        .floatValue());
    }

    public static Secret createSystemSecret(String projectId, ApplicationConfigurationProperties appProperties) {
        Map<String, String> data = Map.of("BACKEND_HOST", appProperties.getServer().getHost() +
                appProperties.getServer().getServlet().getContextPath(),
                "POD_NAMESPACE", projectId);
        return new SecretBuilder()
                .addToStringData(data)
                .withNewMetadata()
                .withName(SYSTEM_SECRET)
                .endMetadata()
                .build();
    }
}
