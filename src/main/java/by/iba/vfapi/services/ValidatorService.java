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
package by.iba.vfapi.services;

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.DataSource;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.jobs.StageType;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.ContainerStageConfig;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static by.iba.vfapi.dto.jobs.StageType.READ;
import static by.iba.vfapi.dto.jobs.StageType.WRITE;

/**
 * Service class for validation pipeline graph node.
 */
@Service
public class ValidatorService {

    private static final int TWO = 2;
    private static final String OPERATION_FIELD = "operation";
    private static final String STORAGE_FIELD = "storage";
    public static final String ONE_INPUT_CHECK_ERR = "%s stage must have one input arrow";
    public static final String TWO_INPUT_CHECK_ERR = "%s stage must have two input arrows";
    public static final String STAGE_DATASOURCE_ERR = "%s stage can have only such data sources, " +
            "since the project is demo: %s";
    private final JobService jobService;
    private final KubernetesService kubernetesService;
    private final PipelineService pipelineService;

    @Autowired
    public ValidatorService(@Lazy JobService jobService, KubernetesService kubernetesService,
                            @Lazy PipelineService pipelineService) {
        this.jobService = jobService;
        this.kubernetesService = kubernetesService;
        this.pipelineService = pipelineService;
    }

    /**
     * Validate pipeline graph
     *
     * @param graphDto  pipeline's graph dto
     * @param projectId project's id
     */
    public void validateGraphPipeline(GraphDto graphDto, String projectId, String id) {
        if (CollectionUtils.isEmpty(graphDto.getEdges()) && CollectionUtils.isEmpty(graphDto.getNodes())) {
            return;
        }
        final Map<String, Consumer<GraphDto.NodeDto>> nodeValidators = Map.of(Constants.NODE_OPERATION_JOB,
                nodeDto -> validateJobNode(nodeDto, projectId),
                Constants.NODE_OPERATION_NOTIFICATION,
                ValidatorService::validateNotificationNode,
                Constants.NODE_OPERATION_CONTAINER,
                nodeDto -> validateContainerNode(nodeDto, projectId),
                Constants.NODE_OPERATION_PIPELINE,
                nodeDto -> validatePipelineNode(nodeDto, projectId, id),
                Constants.NODE_OPERATION_WAIT,
                (GraphDto.NodeDto nodeDto) -> {
                });
        for (GraphDto.NodeDto node : graphDto.getNodes()) {
            if (!node.getValue().containsKey(Constants.NODE_OPERATION)) {
                throw new BadRequestException("Node is missing operation definition");
            }
            String operationType = node.getValue().get(Constants.NODE_OPERATION);
            if (!nodeValidators.containsKey(operationType)) {
                throw new BadRequestException("Unsupported operation type");
            }
            nodeValidators.get(operationType).accept(node);
        }
        final Set<String> allowedSuccessPaths =
                Set.of(Constants.EDGE_SUCCESS_PATH_POSITIVE, Constants.EDGE_SUCCESS_PATH_NEGATIVE);
        for (GraphDto.EdgeDto edge : graphDto.getEdges()) {
            if (!edge.getValue().containsKey(Constants.NODE_OPERATION)) {
                throw new BadRequestException("Edge is missing operation definition");
            }
            if (!edge.getValue().get(Constants.NODE_OPERATION).equals(Constants.NODE_OPERATION_EDGE)) {
                throw new BadRequestException("Edge has invalid operation");
            }
            validateEdgeNode(edge, allowedSuccessPaths);
        }
        validateEdgeConnections(graphDto.getNodes(), graphDto.getEdges());
    }

    /**
     * Validates a single job node against set of special conditions
     *
     * @param jobNode   job node
     * @param projectId id of the project
     */
    private void validateJobNode(GraphDto.NodeDto jobNode, String projectId) {
        Map<String, String> nodeValues = jobNode.getValue();
        if (!nodeValues.containsKey(Constants.NODE_NAME) ||
                nodeValues.get(Constants.NODE_NAME).isEmpty()) {
            throw new BadRequestException("Job node must have a name");
        }
        if (!nodeValues.containsKey(Constants.NODE_JOB_ID)) {
            throw new BadRequestException("Job node is missing job's id");
        }
        try {
            if (!jobService.checkIfJobExists(projectId, nodeValues.get(Constants.NODE_JOB_ID))) {
                throw new BadRequestException("Job node is referring to malformed job");
            }
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Job node is referring to non-existing job", e);
        }
    }

    /**
     * Validates a pipeline node
     *
     * @param pipelineNode pipeline node
     * @param projectId    id of the project
     */
    private void validatePipelineNode(GraphDto.NodeDto pipelineNode, String projectId, String id) {
        String pipelineId = extractPipelineId(pipelineNode);
        if (!pipelineService.checkIfPipelineExists(projectId, pipelineId)) {
            throw new BadRequestException("Pipeline node is referring to non-existing pipeline");
        }
        if (id.equals(pipelineId)) {
            throw new BadRequestException("Pipeline node should start another pipeline, not itself");
        }
    }

    /**
     * Extracts pipeline ID from pipeline node.
     *
     * @param pipelineNode is pipeline node from graph.
     * @return pipeline ID.
     */
    private static String extractPipelineId(GraphDto.NodeDto pipelineNode) {
        Map<String, String> nodeValues = pipelineNode.getValue();
        if (!nodeValues.containsKey(Constants.NODE_NAME) ||
                nodeValues.get(Constants.NODE_NAME).isEmpty()) {
            throw new BadRequestException("Pipeline node must have a name");
        }
        if (!nodeValues.containsKey(Constants.NODE_PIPELINE_ID)) {
            throw new BadRequestException("Pipeline node is missing pipeline's id");
        }
        return nodeValues.get(Constants.NODE_PIPELINE_ID);
    }

    private void validateContainerNode(GraphDto.NodeDto containerNode, String namespace) {
        Map<String, String> nodeValues = containerNode.getValue();
        validateNodeValuesStructure(nodeValues);
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
        ContainerStageConfig.ImagePullSecretType imagePullSecretType;
        try {
            imagePullSecretType = ContainerStageConfig.ImagePullSecretType.valueOf(
                    nodeValues.get(Constants.NODE_IMAGE_PULL_SECRET_TYPE));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Malformed image pull secret type has been provided", e);
        }
        switch (imagePullSecretType) {
            case NEW:
                validateNewPullSecret(nodeValues);
                break;
            case PROVIDED:
                validateProvidedPullSecret(nodeValues, namespace);
                break;
            default:
                break;
        }
    }

    private static void validateNodeValuesStructure(Map<String, String> nodeValues) {
        checkPropertyIfEmpty(Constants.NODE_NAME, nodeValues, "Container node must have a name");
        checkPropertyIfEmpty(Constants.NODE_IMAGE_LINK, nodeValues, "Link to the image has to be specified");
        checkPropertyIfEmpty(Constants.NODE_IMAGE_PULL_POLICY, nodeValues, "Image pull policy has to be specified");
        checkProperties(Set.of(PipelineService.LIMITS_MEMORY, PipelineService.LIMITS_CPU,
                        PipelineService.REQUESTS_MEMORY, PipelineService.REQUESTS_CPU), nodeValues.keySet(),
                "Container's resource configuration has to be specified");
        checkProperties(Set.of(Constants.NODE_IMAGE_PULL_SECRET_TYPE), nodeValues.keySet(),
                "Image pull secret type is missing");
    }

    private static void checkPropertyIfEmpty(String property, Map<String, String> nodeValues, String message) {
        if (!nodeValues.containsKey(property) || nodeValues.get(property).isEmpty()) {
            throw new BadRequestException(message);
        }
    }

    private static void checkProperties(Set<String> properties, Set<String> nodeKeys, String message) {
        if (!nodeKeys.containsAll(properties)) {
            throw new BadRequestException(message);
        }
    }

    private static void validateNewPullSecret(Map<String, String> nodeValues) {
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
    }

    private void validateProvidedPullSecret(Map<String, String> nodeValues, String namespace) {
        if (!nodeValues.containsKey(Constants.NODE_IMAGE_PULL_SECRET_NAME)) {
            throw new BadRequestException("Image pull secret name is missing");
        }
        String imagePullSecretName = nodeValues.get(Constants.NODE_IMAGE_PULL_SECRET_NAME);
        try {
            kubernetesService.getSecret(namespace, imagePullSecretName);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Cannot find a secret with provided name", e);
        }
    }

    /**
     * Validates a single notification node against set of special conditions
     *
     * @param notificationNode notification node
     */
    private static void validateNotificationNode(GraphDto.NodeDto notificationNode) {
        Map<String, String> nodeValues = notificationNode.getValue();
        if (!nodeValues.containsKey(Constants.NODE_NAME) ||
                nodeValues.get(Constants.NODE_NAME).isEmpty()) {
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
    private static void validateEdgeNode(GraphDto.EdgeDto edge, Set<String> allowedSuccessPaths) {
        Map<String, String> edgeValues = edge.getValue();
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
    private static void validateEdgeConnections(List<GraphDto.NodeDto> nodes, List<GraphDto.EdgeDto> edges) {
        if (CollectionUtils.isEmpty(edges)) {
            return;
        }
        if (CollectionUtils.isEmpty(nodes)) {
            throw new BadRequestException("Cannot have the edges without the nodes");
        }
        Set<String> nodeIds = nodes.stream().map(GraphDto.NodeDto::getId).collect(Collectors.toSet());
        for (GraphDto.EdgeDto edge : edges) {
            if (StringUtils.isEmpty(edge.getSource())) {
                throw new BadRequestException("Edge is missing the source");
            }
            if (StringUtils.isEmpty(edge.getTarget())) {
                throw new BadRequestException("Edge is missing the target");
            }
            if (!nodeIds.contains(edge.getSource())) {
                throw new BadRequestException("Edge is referring to non-existing source node");
            }
            if (!nodeIds.contains(edge.getTarget())) {
                throw new BadRequestException("Edge is referring to non-existing target node");
            }
        }
    }

    /**
     * Validate stages connections in jobs.
     *
     * @param graphDto graph with nodes and edges
     */
    public void validateGraph(GraphDto graphDto, Map<String, List<DataSource>> sourcesForDemo) {
        List<GraphDto.NodeDto> nodes = graphDto.getNodes();
        List<String> targets =
                graphDto.getEdges().stream().map(GraphDto.EdgeDto::getTarget).collect(Collectors.toList());
        List<String> sources =
                graphDto.getEdges().stream().map(GraphDto.EdgeDto::getSource).collect(Collectors.toList());
        for (GraphDto.NodeDto node : nodes) {
            long targetsCount = targets.stream().filter(target -> target.equals(node.getId())).count();
            StageType operation = StageType.toStageType(node.getValue().get(OPERATION_FIELD));
            switch (operation) {
                case READ:
                    validateReadStage(targets, node, sourcesForDemo);
                    break;
                case WRITE:
                case VALIDATE:
                    validateWriteStage(operation, sources, node, targetsCount, sourcesForDemo);
                    break;
                case UNION:
                case JOIN:
                case CDC:
                    validateShuffleStage(operation, targetsCount);
                    break;
                default:
                    validateTransformStage(operation, targetsCount);
            }
        }
    }

    /**
     * Secondary method for validating READ stage.
     *
     * @param targets        edges 'target' field.
     * @param node           node object
     * @param sourcesForDemo if project is demo, only these sources should be available. Others are forbidden.
     */
    private static void validateReadStage(List<String> targets, GraphDto.NodeDto node,
                                          Map<String, List<DataSource>> sourcesForDemo) {
        if (targets.contains(node.getId())) {
            throw new BadRequestException("READ stage can have only output arrows");
        }
        if (sourcesForDemo != null) {
            checkSourceAvailability(READ, node, sourcesForDemo);
        }
    }

    /**
     * Secondary method for validating write-type stage.
     *
     * @param operation      stage operation.
     * @param sources        edges 'source' field.
     * @param node           node object
     * @param targetsCount   edges 'target' fields count.
     * @param sourcesForDemo if project is demo, only these sources should be available. Others are forbidden.
     */
    private static void validateWriteStage(StageType operation, List<String> sources, GraphDto.NodeDto node,
                                           long targetsCount, Map<String, List<DataSource>> sourcesForDemo) {
        if (sources.contains(node.getId()) || targetsCount != 1) {
            throw new BadRequestException(String.format(ONE_INPUT_CHECK_ERR, operation.name()));
        }
        if (sourcesForDemo != null) {
            checkSourceAvailability(WRITE, node, sourcesForDemo);
        }
    }

    /**
     * Secondary method for check READ and WRITE stages, if
     * they contain only available for DEMO project data sources.
     *
     * @param stageType          current job's stage.
     * @param node           current job's node.
     * @param sourcesForDemo sources, that are allowed for this stage.
     */
    private static void checkSourceAvailability(StageType stageType, GraphDto.NodeDto node,
                                                Map<String, List<DataSource>> sourcesForDemo) {
        List<DataSource> sources = sourcesForDemo.get(stageType.name());
        if (sources != null) {
            List<String> sourcesStr = sources.stream()
                    .map(DataSource::getValue)
                    .collect(Collectors.toList());
            String sourceInStage = node.getValue().get(STORAGE_FIELD);
            if (!(CollectionUtils.isEmpty(sourcesStr) ||
                    StringUtils.isEmpty(sourceInStage) ||
                    sourcesStr.contains(sourceInStage))) {
                throw new BadRequestException(
                        String.format(STAGE_DATASOURCE_ERR,
                                stageType.name(),
                                String.join(",", sourcesStr)));
            }
        }
    }

    /**
     * Secondary method for validating shuffle-type stage.
     *
     * @param operation    stage operation.
     * @param targetsCount edges 'target' fields count.
     */
    private static void validateShuffleStage(StageType operation, long targetsCount) {
        if (targetsCount != TWO) {
            throw new BadRequestException(String.format(TWO_INPUT_CHECK_ERR, operation.name()));
        }
    }

    /**
     * Secondary method for validating transform-type stage.
     *
     * @param operation    stage operation.
     * @param targetsCount edges 'target' fields count.
     */
    private static void validateTransformStage(StageType operation, long targetsCount) {
        if (targetsCount != 1) {
            throw new BadRequestException(String.format(ONE_INPUT_CHECK_ERR, operation.name()));
        }
    }

}
