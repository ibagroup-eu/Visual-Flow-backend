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

package by.iba.vfapi.dto;

import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.ContainerStageConfig;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.PipelineService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Graph DTO class.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GraphDto {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<NodeDto> nodes;
    private List<EdgeDto> edges;

    /**
     * Parse definition to nodes and edges.
     *
     * @param definition json
     * @return GraphDto with nodes and edges
     */
    public static GraphDto parseGraph(JsonNode definition) {
        ArrayNode nodesArray = definition.withArray("graph");

        List<GraphDto.NodeDto> nodes = new ArrayList<>();
        List<GraphDto.EdgeDto> edges = new ArrayList<>();
        for (JsonNode node : nodesArray) {
            if (node.get("vertex") != null) {
                nodes.add(MAPPER.convertValue(node, GraphDto.NodeDto.class));
            } else {
                edges.add(MAPPER.convertValue(node, GraphDto.EdgeDto.class));
            }
        }

        return new GraphDto(nodes, edges);
    }

    /**
     * Validate pipeline graph
     *
     * @param graphDto          pipeline's graph dto
     * @param projectId         project's id
     * @param kubernetesService k8s service to perform sanity check on jobs
     */
    public static void validateGraphPipeline(
        GraphDto graphDto, String projectId, KubernetesService kubernetesService) {
        final Map<String, Consumer<NodeDto>> nodeValidators = Map.of(Constants.NODE_OPERATION_JOB,
                                                                     nodeDto -> validateJobNode(nodeDto,
                                                                                                projectId,
                                                                                                kubernetesService),
                                                                     Constants.NODE_OPERATION_NOTIFICATION,
                                                                     GraphDto::validateNotificationNode,
                                                                     Constants.NODE_OPERATION_CONTAINER,
                                                                     nodeDto -> validateContainerNode(nodeDto,
                                                                                                      projectId,
                                                                                                      kubernetesService));
        if ((graphDto.edges == null || graphDto.edges.isEmpty()) &&
            (graphDto.nodes == null || graphDto.nodes.isEmpty())) {
            return;
        }
        for (NodeDto node : graphDto.nodes) {
            if (!node.value.containsKey(Constants.NODE_OPERATION)) {
                throw new BadRequestException("Node is missing operation definition");
            }
            String operationType = node.value.get(Constants.NODE_OPERATION);
            if (!nodeValidators.containsKey(operationType)) {
                throw new BadRequestException("Unsupported operation type");
            }
            nodeValidators.get(operationType).accept(node);
        }
        final Set<String> allowedSuccessPaths =
            Set.of(Constants.EDGE_SUCCESS_PATH_POSITIVE, Constants.EDGE_SUCCESS_PATH_NEGATIVE);
        for (EdgeDto edge : graphDto.edges) {
            if (!edge.value.containsKey(Constants.NODE_OPERATION)) {
                throw new BadRequestException("Edge is missing operation definition");
            }
            if (!edge.value.get(Constants.NODE_OPERATION).equals(Constants.NODE_OPERATION_EDGE)) {
                throw new BadRequestException("Edge has invalid operation");
            }
            validateEdgeNode(edge, allowedSuccessPaths);
        }
        validateEdgeConnections(graphDto.nodes, graphDto.edges);
    }

    /**
     * Validates a single job node against set of special conditions
     *
     * @param jobNode   job node
     * @param projectId id of the project
     * @param service   kubernetes service to perform job sanity check
     */
    private static void validateJobNode(NodeDto jobNode, String projectId, KubernetesService service) {
        Map<String, String> nodeValues = jobNode.value;
        if (!nodeValues.containsKey(Constants.NODE_JOB_NAME) ||
            nodeValues.get(Constants.NODE_JOB_NAME).isEmpty()) {
            throw new BadRequestException("Job node must have a name");
        }
        if (!nodeValues.containsKey(Constants.NODE_JOB_ID)) {
            throw new BadRequestException("Job node is missing job's id");
        }
        String jobId = nodeValues.get(Constants.NODE_JOB_ID);
        try {
            ConfigMap jobConfigMap = service.getConfigMap(projectId, jobId);
            if (jobConfigMap == null) {
                throw new BadRequestException("Job node is referring to malformed job");
            }
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Job node is referring to non-existing job", e);
        }
    }

    private static void validateContainerNode(
        NodeDto containerNode, String namespace, KubernetesService kubernetesService) {
        Map<String, String> nodeValues = containerNode.value;
        if (!nodeValues.containsKey(Constants.NODE_CONTAINER_NAME) ||
            nodeValues.get(Constants.NODE_CONTAINER_NAME).isEmpty()) {
            throw new BadRequestException("Container node must have a name");
        }
        if (!nodeValues.containsKey(Constants.NODE_IMAGE_LINK) ||
            nodeValues.get(Constants.NODE_IMAGE_LINK).isEmpty()) {
            throw new BadRequestException("Link to the image has to be specified");
        }
        if (!nodeValues.containsKey(Constants.NODE_IMAGE_PULL_POLICY) ||
            nodeValues.get(Constants.NODE_IMAGE_PULL_POLICY).isEmpty()) {
            throw new BadRequestException("Image pull policy has to be specified");
        }
        if (!nodeValues.containsKey(PipelineService.LIMITS_MEMORY) ||
            !nodeValues.containsKey(PipelineService.LIMITS_CPU) ||
            !nodeValues.containsKey(PipelineService.REQUESTS_MEMORY) ||
            !nodeValues.containsKey(PipelineService.REQUESTS_CPU)) {
            throw new BadRequestException("Container's resource configuration has to be specified");
        }
        try {
            Quantity.getAmountInBytes(Quantity.parse(nodeValues.get(PipelineService.LIMITS_MEMORY)));
            Quantity.getAmountInBytes(Quantity.parse(StringUtils.stripEnd(nodeValues.get(PipelineService.LIMITS_CPU),
                                                                          "cC")));
            Quantity.getAmountInBytes(Quantity.parse(nodeValues.get(PipelineService.REQUESTS_MEMORY)));
            Quantity.getAmountInBytes(Quantity.parse(StringUtils.stripEnd(nodeValues.get(PipelineService.REQUESTS_CPU),
                                                                          "cC")));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("One setting from the resource configuration has been provided in " +
                                              "incorrect format", e);
        }
        if (!nodeValues.containsKey(Constants.NODE_IMAGE_PULL_SECRET_TYPE)) {
            throw new BadRequestException("Image pull secret type is missing");
        }
        ContainerStageConfig.ImagePullSecretType imagePullSecretType;
        try {
            imagePullSecretType =
                ContainerStageConfig.ImagePullSecretType.valueOf(nodeValues.get(Constants.NODE_IMAGE_PULL_SECRET_TYPE));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Malformed image pull secret type has been provided", e);
        }
        switch (imagePullSecretType) {
            case NEW:
                List<String> registryConfigParams = new ArrayList<>();
                registryConfigParams.add(nodeValues.get(Constants.NODE_REGISTRY_LINK));
                registryConfigParams.add(nodeValues.get(Constants.NODE_USERNAME));
                registryConfigParams.add(nodeValues.get(Constants.NODE_PASSWORD));
                long providedConfigParams = registryConfigParams.stream().filter(Objects::nonNull).count();
                if (providedConfigParams != registryConfigParams.size()) {
                    throw new BadRequestException(
                        "In order to create a new image pull secret you have to specify link to the registry, a " +
                            "username and a password");
                }
                break;
            case PROVIDED:
                if (!nodeValues.containsKey(Constants.NODE_IMAGE_PULL_SECRET_NAME)) {
                    throw new BadRequestException("Image pull secret name is missing");
                }
                String imagePullSecretName = nodeValues.get(Constants.NODE_IMAGE_PULL_SECRET_NAME);
                try {
                    kubernetesService.getSecret(namespace, imagePullSecretName);
                } catch (ResourceNotFoundException e) {
                    throw new BadRequestException("Cannot find a secret with provided name", e);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Validates a single notification node against set of special conditions
     *
     * @param notificationNode notification node
     */
    private static void validateNotificationNode(NodeDto notificationNode) {
        Map<String, String> nodeValues = notificationNode.value;
        if (!nodeValues.containsKey(Constants.NODE_NOTIFICATION_NAME) ||
            nodeValues.get(Constants.NODE_NOTIFICATION_NAME).isEmpty()) {
            throw new BadRequestException("Notification node must have a name");
        }
        if (!nodeValues.containsKey(Constants.NODE_NOTIFICATION_MESSAGE)) {
            throw new BadRequestException("Notification node is missing the message");
        }
        if (!nodeValues.containsKey(Constants.NODE_NOTIFICATION_RECIPIENTS) ||
            nodeValues.get(Constants.NODE_NOTIFICATION_RECIPIENTS).isEmpty()) {
            throw new BadRequestException("Notification node requires at least one recipient");
        }
    }

    /**
     * Validates a single edge node against set of special conditions
     *
     * @param edge                edge node
     * @param allowedSuccessPaths set of allowed values for success path property
     */
    private static void validateEdgeNode(EdgeDto edge, Set<String> allowedSuccessPaths) {
        Map<String, String> edgeValues = edge.value;
        if (!edgeValues.containsKey(Constants.EDGE_SUCCESS_PATH)) {
            throw new BadRequestException("Edge is missing successPath");
        }
        if (!allowedSuccessPaths.contains(edgeValues.get(Constants.EDGE_SUCCESS_PATH))) {
            throw new BadRequestException("Edge has invalid successPath");
        }
    }

    /**
     * Validates connections between nodes through edges
     *
     * @param nodes list of nodes
     * @param edges list of edges
     */
    private static void validateEdgeConnections(List<NodeDto> nodes, List<EdgeDto> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new BadRequestException("Cannot have the edges without the nodes");
        }
        Set<String> nodeIds = nodes.stream().map(NodeDto::getId).collect(Collectors.toSet());
        for (EdgeDto edge : edges) {
            if (edge.source == null || edge.source.isEmpty()) {
                throw new BadRequestException("Edge is missing the source");
            }
            if (edge.target == null || edge.target.isEmpty()) {
                throw new BadRequestException("Edge is missing the target");
            }
            if (!nodeIds.contains(edge.source)) {
                throw new BadRequestException("Edge is referring to non-existing source node");
            }
            if (!nodeIds.contains(edge.target)) {
                throw new BadRequestException("Edge is referring to non-existing target node");
            }
        }
    }

    /**
     * Creating data for configMap.
     *
     * @return data Map(String, String)
     */
    public Map<String, String> createConfigMapData() {
        try {
            return Map.of(Constants.JOB_CONFIG_FIELD, MAPPER.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Bad graph structure", e);
        }
    }

    /**
     * Node Dto class.
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeDto {
        private String id;
        private Map<String, String> value;
    }

    /**
     * Edge DTO class
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EdgeDto {
        private Map<String, String> value;
        private String source;
        private String target;
    }
}
