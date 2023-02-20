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

package by.iba.vfapi.model;

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.PipelineService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import static by.iba.vfapi.dto.Constants.NODE_IMAGE_LINK;
import static by.iba.vfapi.dto.Constants.NODE_IMAGE_PULL_POLICY;
import static by.iba.vfapi.dto.Constants.NODE_IMAGE_PULL_SECRET_NAME;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_CONTAINER;
import static by.iba.vfapi.dto.Constants.NODE_PASSWORD;
import static by.iba.vfapi.dto.Constants.NODE_REGISTRY_LINK;
import static by.iba.vfapi.dto.Constants.NODE_START_COMMAND;
import static by.iba.vfapi.dto.Constants.NODE_USERNAME;

/**
 * Represents configuration for custom container stage for pipeline
 */
@Getter
@Builder
@EqualsAndHashCode
@AllArgsConstructor
public class ContainerStageConfig {
    static final Pattern PARAM_PATTERN = Pattern.compile("^#([A-Za-z0-9\\\\-_]{1,50})#$");
    private final String imageLink;
    private final String imagePullPolicy;
    private final String limitsCpu;
    private final String limitsMemory;
    private final String requestCpu;
    private final String requestMemory;
    private final String registryLink;
    private final String username;
    private final String password;
    private final String startCommand;
    private final boolean mountProjectParams;
    private final ImagePullSecretType imagePullSecretType;
    private Secret secret;

    /**
     * Creates config based on retrieved information
     * This method assumes that the data has already been validated by GraphDto methods
     *
     * @param node              node with the data
     * @param projectParams     project parameters retrieved from the secret
     * @param namespace         project namespace in k8s
     * @param kubernetesService k8s service
     * @return configuration for custom container stage
     * @see GraphDto#validateContainerNode(GraphDto.NodeDto, String, KubernetesService)
     */
    public static ContainerStageConfig fromContainerNode(
        GraphDto.NodeDto node,
        @Valid Map<String, ParamDto> projectParams,
        String namespace,
        KubernetesService kubernetesService) {
        if (!NODE_OPERATION_CONTAINER.equals(node.getValue().get(NODE_OPERATION))) {
            throw new BadRequestException("Unsupported node type");
        }
        ContainerStageConfigBuilder builder = ContainerStageConfig
            .builder()
            .imageLink(tryToFetchFromParams(node.getValue().get(NODE_IMAGE_LINK), projectParams))
            .imagePullPolicy(node.getValue().get(NODE_IMAGE_PULL_POLICY))
            .requestCpu(StringUtils.stripEnd(node.getValue().get(PipelineService.REQUESTS_CPU), "cC"))
            .limitsCpu(StringUtils.stripEnd(node.getValue().get(PipelineService.LIMITS_CPU), "cC"))
            .limitsMemory(node.getValue().get(PipelineService.LIMITS_MEMORY))
            .requestMemory(node.getValue().get(PipelineService.REQUESTS_MEMORY))
            .mountProjectParams(Optional
                                    .ofNullable(node.getValue().get(Constants.NODE_MOUNT_PROJECT_PARAMS))
                                    .map(Boolean::parseBoolean)
                                    .orElse(false))
            .imagePullSecretType(ImagePullSecretType.valueOf(node
                                                                 .getValue()
                                                                 .get(Constants.NODE_IMAGE_PULL_SECRET_TYPE)));
        switch (builder.imagePullSecretType) {
            case NEW:
                builder
                    .username(tryToFetchFromParams(node.getValue().get(NODE_USERNAME), projectParams))
                    .password(tryToFetchFromParams(node.getValue().get(NODE_PASSWORD), projectParams))
                    .registryLink(tryToFetchFromParams(node.getValue().get(NODE_REGISTRY_LINK), projectParams));
                break;
            case PROVIDED:
                String secretName = tryToFetchFromParams(node.getValue().get(NODE_IMAGE_PULL_SECRET_NAME),
                        projectParams);
                if (kubernetesService.isSecretExist(namespace, secretName)) {
                    builder.secret(kubernetesService.getSecret(namespace, secretName));
                } else {
                    throw new BadRequestException("Cannot find a secret with provided name");
                }
                break;
            case NOT_APPLICABLE:
            default:
                break;
        }
        String startCommand = tryToFetchFromParams(node.getValue().get(NODE_START_COMMAND), projectParams);
        if (startCommand != null) {
            builder.startCommand(startCommand);
        }
        return builder.build();
    }


    /**
     * Helper method that tries to fetch the value from project parameters if the initial value matches the
     * project param pattern
     *
     * @param initialVal    initial value retrieved from the node
     * @param projectParams project parameters
     * @return if initial value is null - null, if either initial value doesn't match the pattern or param with
     * such name doesn't exist - initialValue, otherwise param value
     */
    private static String tryToFetchFromParams(String initialVal, Map<String, ParamDto> projectParams) {
        if (initialVal == null) {
            return null;
        }
        Matcher matcher = PARAM_PATTERN.matcher(initialVal);
        if (!matcher.matches()) {
            return initialVal;
        }
        String paramName = matcher.group(1);
        if (!projectParams.containsKey(paramName)) {
            return initialVal;
        }
        return projectParams.get(paramName).getValue().getText();
    }

    /**
     * Prepares a new k8s image pull secret composed of username, password and registry link
     *
     * @param name       secret's name
     * @param pipelineId pipeline id to include it within labels
     */
    public void prepareNewSecret(String name, String pipelineId) {
        try {
            if (ImagePullSecretType.NEW != imagePullSecretType) {
                return;
            }
            Map<String, Object> dockerConfigMap = new HashMap<>();
            Map<String, Object> auths = new HashMap<>();
            Map<String, Object> credentials = new HashMap<>();
            String usernameAndPasswordAuth = username + ":" + password;
            credentials.put("auth",
                            Base64
                                .getEncoder()
                                .encodeToString(usernameAndPasswordAuth.getBytes(StandardCharsets.UTF_8)));
            auths.put(registryLink, credentials);
            dockerConfigMap.put("auths", auths);
            String dockerConfigAsStr = Serialization.jsonMapper().writeValueAsString(dockerConfigMap);
            this.secret = new SecretBuilder()
                .withType("kubernetes.io/dockerconfigjson")
                .addToData(".dockerconfigjson",
                           Base64.getEncoder().encodeToString(dockerConfigAsStr.getBytes(StandardCharsets.UTF_8)))
                .withNewMetadata()
                .withName(name)
                .addToLabels(Constants.PIPELINE_ID_LABEL, pipelineId)
                .addToLabels(Constants.CONTAINER_STAGE, "true")
                .endMetadata()
                .build();
        } catch (JsonProcessingException e) {
            throw new InternalProcessingException("Cannot parse container configuration", e);
        }

    }

    /**
     * Signifies the type of image pull secret
     */
    public enum ImagePullSecretType {
        NOT_APPLICABLE, NEW, PROVIDED
    }

}
