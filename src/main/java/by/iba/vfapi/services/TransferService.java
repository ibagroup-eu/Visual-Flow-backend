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
import by.iba.vfapi.dto.exporting.ExportRequestDto;
import by.iba.vfapi.dto.exporting.ExportResponseDto;
import by.iba.vfapi.dto.exporting.PipelinesWithRelatedJobs;
import by.iba.vfapi.dto.importing.EntityDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.ImagePullSecret;
import by.iba.vfapi.model.argo.PipelineParams;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

/**
 * TransferService class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
// This class contains a sonar vulnerability - java:S1200: Split this class into smaller and more specialized ones to
// reduce its dependencies on other classes from 31 to the maximum authorized 30 or less. This means, that this class
// should not be coupled to too many other classes (Single Responsibility Principle).
public class TransferService {
    private static final Pattern SHARPS_REGEX = Pattern.compile("^#(.+?)#$");
    private final ArgoKubernetesService argoKubernetesService;
    private final JobService jobService;
    private final PipelineService pipelineService;
    private final ProjectService projectService;

    /**
     * Exporting jobs by ids.
     *
     * @param projectId project id
     * @param jobIds    jobs ids to export
     * @return list of jsons
     */
    private Set<ConfigMap> exportJobs(final String projectId, final Set<String> jobIds) {
        Set<ConfigMap> exportedJobs = Sets.newHashSetWithExpectedSize(jobIds.size());
        for (String jobId : jobIds) {
            ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, jobId);
            ObjectMeta metadata = configMap.getMetadata();
            ConfigMap configMapForExport = new ConfigMapBuilder()
                    .withMetadata(new ObjectMetaBuilder()
                            .withName(metadata.getName())
                            .addToAnnotations(metadata.getAnnotations())
                            .addToLabels(metadata.getLabels())
                            .build())
                    .withData(configMap.getData())
                    .build();
            exportedJobs.add(configMapForExport);
        }

        return exportedJobs;
    }

    /**
     * Exporting pipelines by ids.
     *
     * @param projectId project id
     * @param pipelines jobs ids to export
     * @return list of jsons
     */
    private Set<PipelinesWithRelatedJobs> exportPipelines(
            final String projectId, final Set<ExportRequestDto.PipelineRequest> pipelines) {
        Set<PipelinesWithRelatedJobs> exportedPipelines = Sets.newHashSetWithExpectedSize(pipelines.size());
        for (ExportRequestDto.PipelineRequest pipeline : pipelines) {
            WorkflowTemplate workflowTemplate =
                    argoKubernetesService.getWorkflowTemplate(projectId, pipeline.getPipelineId());
            ObjectMeta metadata = workflowTemplate.getMetadata();
            WorkflowTemplateSpec spec = workflowTemplate.getSpec();

            WorkflowTemplate workflowTemplateForExport = new WorkflowTemplate();
            workflowTemplateForExport.setMetadata(new ObjectMetaBuilder()
                    .withName(metadata.getName())
                    .addToAnnotations(metadata.getAnnotations())
                    .addToLabels(metadata.getLabels())
                    .addToLabels("type", "pipeline")
                    .build());
            workflowTemplateForExport.setSpec(spec);
            PipelinesWithRelatedJobs pipelinesWithRelatedJobs =
                    new PipelinesWithRelatedJobs(workflowTemplateForExport);

            if (pipeline.isWithRelatedJobs()) {
                List<String> jobIds = PipelineService
                        .getDagTaskFromWorkflowTemplateSpec(spec)
                        .stream()
                        .map(task -> task
                                .getArguments()
                                .getParameters()
                                .stream()
                                .filter(param -> K8sUtils.CONFIGMAP.equals(param.getName()))
                                .findFirst())
                        .filter(Optional::isPresent)
                        .map(parameter -> parameter.get().getValue())
                        .collect(Collectors.toList());
                pipelinesWithRelatedJobs.getRelatedJobIds().addAll(jobIds);
            }

            exportedPipelines.add(pipelinesWithRelatedJobs);
        }

        return exportedPipelines;
    }

    /**
     * Appends missing project args to DTO.
     *
     * @param missingProjectArgs container for missing args
     * @param kind                 flag whether it is job or pipeline
     * @param graph                graph of job or pipeline
     * @param existingArgs       existing project args
     */
    private static void appendMissingArgs(
            String id,
            Map<String, List<EntityDto>> missingProjectArgs,
            String kind,
            GraphDto graph,
            List<String> existingArgs) {

        List<GraphDto.NodeDto> nodes = graph.getNodes();
        List<String> requiredProjectArgs = new ArrayList<>();
        List<String> nodeIds = new ArrayList<>();
        for (GraphDto.NodeDto node : nodes) {
            for (Map.Entry<String, String> entry : node.getValue().entrySet()) {
                Matcher matcher = SHARPS_REGEX.matcher(entry.getValue());
                if (matcher.find()) {
                    nodeIds.add(node.getId());
                    requiredProjectArgs.add(matcher.group(1));
                }
            }
        }
        int index = 0;
        for (String requiredArgs : requiredProjectArgs) {
            if (!existingArgs.contains(requiredArgs)) {
                if (missingProjectArgs.containsKey(requiredArgs)) {
                    List<EntityDto> entityDtos = missingProjectArgs.get(requiredArgs);
                    entityDtos.add(EntityDto.builder().id(id).kind(kind).nodeId(nodeIds.get(index)).build());
                    missingProjectArgs.put(requiredArgs, entityDtos);
                } else {
                    List<EntityDto> entityDto = new ArrayList<>();
                    entityDto.add(EntityDto.builder().id(id).kind(kind).nodeId(nodeIds.get(index)).build());
                    missingProjectArgs.put(requiredArgs, entityDto);
                }
            }
            index++;
        }
    }

    /**
     * Appends missing project args from job to DTO.
     *
     * @param configMap            job's configmap
     * @param missingProjectArgs container for missing args
     * @param existingArgs       existing project args
     */
    private static void appendJobsMissingArgs(
            ConfigMap configMap, Map<String, List<EntityDto>> missingProjectArgs, List<String> existingArgs) {

        JsonNode definition;
        try {
            definition = new ObjectMapper().readTree(Base64.decodeBase64(configMap
                    .getMetadata()
                    .getAnnotations()
                    .get(Constants.DEFINITION)));
        } catch (IOException e) {
            throw new BadRequestException("Invalid job definition JSON", e);
        }
        String kind = Constants.KIND_JOB;
        String id = configMap.getMetadata().getName();
        GraphDto graph = GraphDto.parseGraph(definition);
        appendMissingArgs(id, missingProjectArgs, kind, graph, existingArgs);

    }

    /**
     * Importing jobs.
     *
     * @param projectId                 project id
     * @param jsonJobs                  jsonJobs for import
     * @param missingProjectParams      missing project params
     * @param existingParams            existing project params
     * @param missingProjectConnections missing project connections
     * @param existingConnections       existing project connections
     * @return list with not imported jobs ids
     */
    private List<String> importJobs(
            final String projectId,
            final Set<ConfigMap> jsonJobs,
            Map<String, List<EntityDto>> missingProjectParams,
            List<String> existingParams,
            Map<String, List<EntityDto>> missingProjectConnections,
            List<String> existingConnections) {

        List<String> notImported = new LinkedList<>();
        for (ConfigMap configMap : jsonJobs) {
            String id = configMap.getMetadata().getName();
            String name = configMap.getMetadata().getLabels().get(Constants.NAME);
            configMap
                    .getMetadata()
                    .getAnnotations()
                    .put(Constants.LAST_MODIFIED, ZonedDateTime.now().format(Constants.DATE_TIME_FORMATTER));
            try {
                jobService.checkJobName(projectId, id, name);
                argoKubernetesService.createOrReplaceConfigMap(projectId, configMap);
                appendJobsMissingArgs(configMap, missingProjectParams, existingParams);
                appendJobsMissingArgs(configMap, missingProjectConnections, existingConnections);
            } catch (BadRequestException ex) {
                LOGGER.error(ex.getMessage(), ex);
                notImported.add(id);
            }
        }

        return notImported;
    }

    /**
     * Appends missing project args from pipeline to DTO.
     *
     * @param workflowTemplate     workflow template
     * @param missingProjectArgs container for missing args
     * @param existingArgs       existing project args
     */
    private static void appendPipelinesMissingArgs(
            WorkflowTemplate workflowTemplate,
            Map<String, List<EntityDto>> missingProjectArgs,
            List<String> existingArgs) {

        JsonNode definition;
        try {
            definition = new ObjectMapper().readTree(Base64.decodeBase64(workflowTemplate
                    .getMetadata()
                    .getAnnotations()
                    .get(Constants.DEFINITION)));
        } catch (IOException e) {
            throw new BadRequestException("Invalid pipeline definition JSON", e);
        }
        String kind = Constants.KIND_PIPELINE;
        String id = workflowTemplate.getMetadata().getName();
        GraphDto graph = GraphDto.parseGraph(definition);
        appendMissingArgs(id, missingProjectArgs, kind, graph, existingArgs);
    }

    /**
     * Importing pipelines.
     *
     * @param projectId                 project id
     * @param jsonPipelines             jsonPipelines for import
     * @param missingProjectParams      container for missing parameters
     * @param existingParams            existing project params
     * @param missingProjectConnections container for missing connections
     * @param existingConnections       existing project connections
     * @return list with not imported pipelines ids
     */
    private List<String> importPipelines(
            final String projectId,
            final Set<WorkflowTemplate> jsonPipelines,
            Map<String, List<EntityDto>> missingProjectParams,
            List<String> existingParams,
            Map<String, List<EntityDto>> missingProjectConnections,
            List<String> existingConnections) {

        List<String> notImported = new LinkedList<>();

        for (WorkflowTemplate workflowTemplate : jsonPipelines) {
            String name = workflowTemplate.getMetadata().getLabels().get(Constants.NAME);
            String id = workflowTemplate.getMetadata().getName();
            WorkflowTemplate existingWfTemplate = null;
            try {
                existingWfTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
            } catch (ResourceNotFoundException e) {
                LOGGER.info("Pipeline with id {} doesn't exist, the import procedure will create a new one: {}",
                        id, e.getLocalizedMessage());
            }
            try {
                pipelineService.checkPipelineName(projectId, id, name);
                String encodedDefinition =
                        workflowTemplate.getMetadata().getAnnotations().get(Constants.DEFINITION);
                if (encodedDefinition == null) {
                    throw new BadRequestException(String.format("Workflow template with id=%s, name=%s is " +
                            "missing definition graph", id, name));
                }
                ObjectMapper mapper = new ObjectMapper();
                JsonNode parsedDefinition = mapper.readTree(new String(Base64.decodeBase64(encodedDefinition),
                        Charset.defaultCharset()));
                PipelineParams params = workflowTemplate.getSpec().getPipelineParams();
                if (existingWfTemplate == null) {
                    argoKubernetesService.createOrReplaceWorkflowTemplate(projectId,
                            pipelineService.createWorkflowTemplate(
                                    projectId,
                                    id,
                                    name,
                                    parsedDefinition,
                                    params));
                } else {
                    LOGGER.info("Pipeline with id {} will be updated via import operation", id);
                    pipelineService.update(projectId, id, parsedDefinition, params,name);
                }
                appendPipelinesMissingArgs(workflowTemplate, missingProjectParams, existingParams);
                appendPipelinesMissingArgs(workflowTemplate, missingProjectConnections, existingConnections);
            } catch (BadRequestException | JsonProcessingException ex) {
                LOGGER.error(ex.getMessage(), ex);
                notImported.add(id);
            }
        }

        return notImported;
    }

    /**
     * Export jobs and pipelines.
     *
     * @param projectId project id
     * @param jobIds    job ids for import
     * @param pipelines pipelines ids and flag with jobs
     * @return pipelines and jobs in json format
     */
    public ExportResponseDto exporting(
            final String projectId, final Set<String> jobIds, final Set<ExportRequestDto.PipelineRequest> pipelines) {
        Set<PipelinesWithRelatedJobs> pipelinesWithRelatedJobs = exportPipelines(projectId, pipelines);

        Set<String> jobsWithPipelineJobsIds = new HashSet<>(jobIds);
        Set<WorkflowTemplate> exportedPipelines = new HashSet<>();

        pipelinesWithRelatedJobs.forEach((PipelinesWithRelatedJobs pipelineWithRelatedJobs) -> {
            jobsWithPipelineJobsIds.addAll(pipelineWithRelatedJobs.getRelatedJobIds());
            exportedPipelines.add(pipelineWithRelatedJobs.getWorkflowTemplate());
        });

        return ExportResponseDto
                .builder()
                .jobs(exportJobs(projectId, jobsWithPipelineJobsIds))
                .pipelines(exportedPipelines)
                .build();
    }

    /**
     * Import jobs and pipelines.
     *
     * @param projectId     project id
     * @param jsonJobs      jobs in json format
     * @param jsonPipelines pipelines in json format
     * @return not imported pipelines and jobs ids
     */
    public ImportResponseDto importing(
            String projectId, Set<ConfigMap> jsonJobs, Set<WorkflowTemplate> jsonPipelines) {
        Map<String, List<EntityDto>> missingProjectParams = new HashMap<>();
        Map<String, List<EntityDto>> missingProjectConnections = new HashMap<>();
        List<String> existingParams = projectService
                .getParams(projectId)
                .getParams()
                .stream()
                .map(ParamDto::getKey)
                .collect(Collectors.toList());
        List<String> existingConnections = projectService
                .getConnections(projectId)
                .getConnections()
                .stream()
                .map(ConnectDto::getKey)
                .collect(Collectors.toList());
        List<String> notImportedJobs = importJobs(projectId, jsonJobs, missingProjectParams, existingParams,
                missingProjectConnections, existingConnections);
        List<String> notImportedPipelines =
                importPipelines(projectId, jsonPipelines, missingProjectParams, existingParams,
                        missingProjectConnections, existingConnections);

        return ImportResponseDto
                .builder()
                .notImportedJobs(notImportedJobs)
                .notImportedPipelines(notImportedPipelines)
                .missingProjectParams(missingProjectParams)
                .missingProjectConnections(missingProjectConnections)
                .build();
    }

    /**
     * Checks if user has permission to use import functionality
     *
     * @param projectId id of the project
     * @return true if user has the access
     */
    public boolean checkImportAccess(String projectId) {
        return argoKubernetesService.isAccessible(projectId, "configmaps", "", Constants.CREATE_ACTION);
    }

    /**
     * Copies job.
     *
     * @param projectId project id
     * @param jobId     job id
     */
    public void copyJob(final String projectId, final String jobId) {
        ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, jobId);
        copyEntity(projectId,
                configMap,
                (projId, confMap) -> jobService.createFromConfigMap(projId, confMap, false),
                argoKubernetesService.getAllConfigMaps(projectId));

    }

    /**
     * Copies pipeline.
     *
     * @param projectId  project id
     * @param pipelineId pipeline id
     */
    public void copyPipeline(final String projectId, final String pipelineId) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, pipelineId);
        String newPipelineId =
                KubernetesService.getUniqueEntityName((String newId) -> argoKubernetesService.getWorkflowTemplate(
                        projectId,
                        newId));
        copyEntity(projectId, workflowTemplate, (String projId, WorkflowTemplate wfTemplate) -> {

            Set<Secret> imagePullSecrets = new HashSet<>(argoKubernetesService.getSecretsByLabels(projectId,
                    new HashMap<>(Map.of(
                            Constants.PIPELINE_ID_LABEL,
                            pipelineId,
                            Constants.CONTAINER_STAGE,
                            "true"))));

            imagePullSecrets.forEach((Secret s) -> {
                s.getMetadata().getLabels().replace(Constants.PIPELINE_ID_LABEL, newPipelineId);
                String newSecretName =
                        KubernetesService.getUniqueEntityName((String sName) -> argoKubernetesService.getSecret(
                                projectId,
                                sName));
                s.getMetadata().setName(newSecretName);
                argoKubernetesService.createOrReplaceSecret(projectId, s);
            });
            ArrayList<ImagePullSecret> listOfSecrets = new ArrayList<>();
            listOfSecrets.add(new ImagePullSecret().name(pipelineService.getImagePullSecret()));
            imagePullSecrets.forEach((Secret sec) -> listOfSecrets.add(new ImagePullSecret().name(sec
                    .getMetadata()
                    .getName())));
            wfTemplate.getSpec().setImagePullSecrets(listOfSecrets);
            wfTemplate.getMetadata().setName(newPipelineId);
            argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, wfTemplate);
        }, argoKubernetesService.getAllWorkflowTemplates(projectId));
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
    private static <T extends HasMetadata> void copyEntity(
            final String projectId, T entityMetadata, BiConsumer<String, T> saver, List<T> entityMetadataList) {
        String currentName = entityMetadata.getMetadata().getLabels().get(Constants.NAME);
        int availableIndex = getNextEntityCopyIndex(currentName, entityMetadataList);
        if (availableIndex == 1) {
            entityMetadata.getMetadata().getLabels().replace(Constants.NAME, currentName,
                    currentName + "-Copy");
        } else {
            entityMetadata
                    .getMetadata()
                    .getLabels()
                    .replace(Constants.NAME, currentName, currentName + "-Copy" + availableIndex);

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
    private static <T extends HasMetadata> int getNextEntityCopyIndex(String entityName, List<T> entityMetadataList) {
        Pattern groupIndex = Pattern.compile(String.format("^%s-Copy(\\d+)?$", Pattern.quote(entityName)));
        return entityMetadataList.stream().map((T e) -> {
            String name = e.getMetadata().getLabels().get(Constants.NAME);
            Matcher matcher = groupIndex.matcher(name);
            if (!matcher.matches()) {
                return 0;
            }
            return Optional.ofNullable(matcher.group(1)).map(Integer::valueOf).orElse(1);
        }).max(Comparator.naturalOrder()).map(index -> index + 1).orElse(1);

    }
}
