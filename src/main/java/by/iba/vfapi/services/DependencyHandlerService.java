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
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.PipelineParams;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.boot.json.JsonParseException;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static by.iba.vfapi.dto.Constants.NODE_OPERATION;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_JOB;
import static by.iba.vfapi.dto.Constants.NODE_OPERATION_PIPELINE;

/**
 * DependencyHandlerService class.
 */
@Slf4j
@Service
@Getter
public class DependencyHandlerService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ArgoKubernetesService argoKubernetesService;

    public DependencyHandlerService(
            ArgoKubernetesService argoKubernetesService) {
        this.argoKubernetesService = argoKubernetesService;
    }

    /**
     * Check and update pipeline graph
     *
     * @param newDefinition pipeline's definition
     * @param projectId     project's id
     */
    public void updateDependenciesGraphPipeline(String projectId, String id,
                                                JsonNode newDefinition) {
        JsonNode oldDefinition = getDefinition(argoKubernetesService.getWorkflowTemplate(projectId, id));
        findDifferences(projectId, id, newDefinition, oldDefinition);
    }

    /**
     * Comparing pipelines to identify differences
     *
     * @param projectId     project's id
     * @param id            pipelineId
     * @param newDefinition definition for pipeline with updates
     * @param oldDefinition old definition for pipeline
     */
    private void findDifferences(
            String projectId,
            String id,
            JsonNode newDefinition,
            JsonNode oldDefinition) {

        Set<GraphDto.NodeDto> newNodes = getNodesByDefinition(newDefinition);
        Set<GraphDto.NodeDto> oldNodes = getNodesByDefinition(oldDefinition);

        Set<GraphDto.NodeDto> filteredNewNodes = newNodes
                .stream()
                .filter(node -> !oldNodes
                        .stream()
                        .map(nodeDto -> nodeDto.getValue().get(Constants.NODE_JOB_ID))
                        .collect(Collectors.toSet())
                        .contains(node.getValue().get(Constants.NODE_JOB_ID)))
                .filter(node -> !oldNodes
                        .stream()
                        .map(nodeDto -> nodeDto.getValue().get(Constants.NODE_PIPELINE_ID))
                        .collect(Collectors.toSet())
                        .contains(node.getValue().get(Constants.NODE_PIPELINE_ID)))
                .collect(Collectors.toSet());

        Set<GraphDto.NodeDto> filteredOldNodes = oldNodes
                .stream()
                .filter(node -> !newNodes
                        .stream()
                        .map(nodeDto -> nodeDto.getValue().get(Constants.NODE_JOB_ID))
                        .collect(Collectors.toSet())
                        .contains(node.getValue().get(Constants.NODE_JOB_ID)))
                .filter(node -> !newNodes
                        .stream()
                        .map(nodeDto -> nodeDto.getValue().get(Constants.NODE_PIPELINE_ID))
                        .collect(Collectors.toSet())
                        .contains(node.getValue().get(Constants.NODE_PIPELINE_ID)))
                .collect(Collectors.toSet());

        deleteDependencies(projectId, id, filteredOldNodes);

        addDependencies(projectId, id, filteredNewNodes);

    }

    /**
     * Assign dependencies by node set
     *
     * @param projectId   definition for pipeline with updates
     * @param pipelineId  pipeline's id
     * @param nodes Set of nodes
     */
    public void addDependencies(String projectId, String pipelineId, Iterable<GraphDto.NodeDto> nodes) {
        for (GraphDto.NodeDto node : nodes) {
            if (node.getValue().get(NODE_OPERATION).equals(NODE_OPERATION_JOB)) {
                addJobDependency(projectId, pipelineId, node.getValue().get(Constants.NODE_JOB_ID));
            }

            if (node.getValue().get(NODE_OPERATION).equals(NODE_OPERATION_PIPELINE)) {
                addPipelineDependency(projectId, pipelineId, node.getValue().get(Constants.NODE_PIPELINE_ID));
            }
        }
    }

    /**
     * Removing dependencies by node set
     *
     * @param projectId   definition for pipeline with updates
     * @param pipelineId  pipeline's id
     * @param nodes Set of nodes
     */
    public void deleteDependencies(String projectId, String pipelineId, Iterable<GraphDto.NodeDto> nodes) {
        for (GraphDto.NodeDto node : nodes) {
            if (node.getValue().get(NODE_OPERATION).equals(NODE_OPERATION_JOB)) {
                deleteJobDependency(projectId, pipelineId, node.getValue().get(Constants.NODE_JOB_ID));
            }
            if (node.getValue().get(NODE_OPERATION).equals(NODE_OPERATION_PIPELINE)) {
                deletedPipelineDependency(projectId, pipelineId, node.getValue().get(Constants.NODE_PIPELINE_ID));
            }
        }
    }

    /**
     * Adding pipeline Id to ConfigMap
     *
     * @param projectId   definition for pipeline with updates
     * @param pipelineId  old definition for pipeline
     * @param targetJobId
     */
    public void addJobDependency(String projectId, String pipelineId, String targetJobId) {
        ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, targetJobId);
        Map<String, String> data = configMap.getData();
        Set<String> existingIds = checkIfJobDependsExist(configMap.getData());
        existingIds.add(pipelineId);
        data.put(Constants.DEPENDENT_PIPELINE_IDS, String.join(",", existingIds));
        configMap.setData(data);
        argoKubernetesService.createOrReplaceConfigMap(projectId, configMap);
        LOGGER.info(
                "Pipeline dependency {} has been added to the job {}",
                pipelineId,
                targetJobId);
    }

    /**
     * Removing pipeline Id from ConfigMap
     *
     * @param projectId   definition for pipeline with updates
     * @param pipelineId  old definition for pipeline
     * @param targetJobId
     */
    public void deleteJobDependency(String projectId, String pipelineId, String targetJobId) {
        ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, targetJobId);
        Map<String, String> data = configMap.getData();
        Set<String> existingIds = checkIfJobDependsExist(configMap.getData());
        existingIds = existingIds.stream()
                .filter(id -> !pipelineId.equals(id))
                .collect(Collectors.toSet());
        data.put(Constants.DEPENDENT_PIPELINE_IDS, String.join(",", existingIds));
        configMap.setData(data);
        argoKubernetesService.createOrReplaceConfigMap(projectId, configMap);
        LOGGER.info(
                "Pipeline dependency {} has been removed from the job {}",
                pipelineId,
                targetJobId);
    }

    /**
     * Adding a Pipeline ID to another Pipeline's Workflow Template
     *
     * @param projectId   definition for pipeline with updates
     * @param pipelineId  old definition for pipeline
     * @param targetPipelineId
     */
    public void addPipelineDependency(String projectId, String pipelineId, String targetPipelineId) {
        WorkflowTemplate workflowTemplate =
                argoKubernetesService.getWorkflowTemplate(projectId, targetPipelineId);
        PipelineParams pipelineParams = workflowTemplate.getSpec().getPipelineParams();
        Set<String> pipelineIDs = checkIfPipelineDependsExist(pipelineParams);
        pipelineIDs.add(pipelineId);
        workflowTemplate.getSpec().setPipelineParams(pipelineParams.dependentPipelineIds(pipelineIDs));
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
        LOGGER.info(
                "Pipeline dependency {} has been added to the pipeline {}",
                pipelineId,
                targetPipelineId);
    }

    /**
     * Removing a Pipeline ID from another Pipeline's Workflow Template
     *
     * @param projectId   definition for pipeline with updates
     * @param pipelineId  old definition for pipeline
     * @param targetPipelineId
     */
    public void deletedPipelineDependency(String projectId, String pipelineId, String targetPipelineId) {
        WorkflowTemplate workflowTemplate =
                argoKubernetesService.getWorkflowTemplate(projectId, targetPipelineId);
        PipelineParams pipelineParams = workflowTemplate.getSpec().getPipelineParams();
        Set<String> pipelineIDs = checkIfPipelineDependsExist(pipelineParams);
        pipelineIDs = pipelineIDs.stream()
                .filter(id -> !pipelineId.equals(id))
                .collect(Collectors.toSet());
        workflowTemplate.getSpec().setPipelineParams(pipelineParams.dependentPipelineIds(pipelineIDs));
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
        LOGGER.info(
                "Pipeline dependency {} has been removed from the pipeline {}",
                pipelineId,
                targetPipelineId);
    }

    /**
     * Retrieve nodes from definition
     *
     * @param jsonNode jsonNode
     * @return Set<GraphDto.NodeDto> Set of nodes without duplicates
     */
    public Set<GraphDto.NodeDto> getNodesByDefinition(JsonNode jsonNode) {
        try {
            return new HashSet<>(GraphDto.parseGraph(Objects.requireNonNullElse(jsonNode,
                            MAPPER.readTree("{\"graph\":[]}")))
                    .getNodes());
        } catch (IOException e) {
            LOGGER.error("An error occurred while parsing: {}", e.getLocalizedMessage());
            throw new BadRequestException("Required definition has incorrect structure");
        }
    }

    /**
     * Getting a dependency existence check to use by the pipeline
     *
     * @param configMap configMap
     * @return boolean true/false
     */
    public boolean jobHasDepends(ConfigMap configMap) {
        return checkIfJobDependsExist(configMap
                .getData())
                .isEmpty();
    }

    /**
     * Getting a dependency existence check to use by the another pipeline
     *
     * @param workflowTemplate  workflowTemplate
     * @return boolean true/false
     */
    public boolean pipelineHasDepends(WorkflowTemplate workflowTemplate) {
        return checkIfPipelineDependsExist(workflowTemplate
                .getSpec()
                .getPipelineParams())
                .isEmpty();
    }

    /**
     * Retrieve definition from metadata
     *
     * @param metadata metadata
     * @return JsonNode definition
     */
    public static JsonNode getDefinition(HasMetadata metadata) {
        try {
            return MAPPER
                    .readTree(Base64.decodeBase64(metadata
                            .getMetadata()
                            .getAnnotations()
                            .get(Constants.DEFINITION)));
        } catch (IOException e) {
            throw new JsonParseException(e);
        }
    }

    /**
     * Getting dependent pipeline Ids if they exist in config map.
     *
     * @param data config map data
     * @return List of params or empty
     */
    public static Set<String> checkIfJobDependsExist(Map<String, String> data) {
        if (data.get(Constants.DEPENDENT_PIPELINE_IDS) != null &&
                !data.get(Constants.DEPENDENT_PIPELINE_IDS).isEmpty()) {
            return new HashSet<>(Arrays.asList(data.get(Constants.DEPENDENT_PIPELINE_IDS).split(",")));
        }
        return new HashSet<>();
    }

    /**
     * Getting dependent pipeline Ids if they exist in workflowTemplate.
     *
     * @param pipelineParams config map data
     * @return List of params or empty
     */
    public static Set<String> checkIfPipelineDependsExist(PipelineParams pipelineParams) {
        if (pipelineParams.getDependentPipelineIds() != null &&
                !pipelineParams.getDependentPipelineIds().isEmpty()) {
            return pipelineParams.getDependentPipelineIds();
        }
        return new HashSet<>();
    }

}
