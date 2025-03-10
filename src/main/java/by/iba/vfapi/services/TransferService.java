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

import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.exporting.ExportRequestDto;
import by.iba.vfapi.dto.exporting.ExportResponseDto;
import by.iba.vfapi.dto.exporting.PipelinesWithRelatedJobs;
import by.iba.vfapi.dto.graph.DefinitionDto;
import by.iba.vfapi.dto.graph.StageDto;
import by.iba.vfapi.dto.importing.ImportParamsDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.dto.importing.MissingParamDto;
import by.iba.vfapi.dto.jobs.JobDto;
import by.iba.vfapi.dto.pipelines.PipelineDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.PipelineParams;
import by.iba.vfapi.services.utils.GraphUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static by.iba.vfapi.dto.Constants.*;

/**
 * TransferService class.
 */
@Slf4j
@Service
// This class contains a sonar vulnerability - java:S1200: Split this class into smaller and more specialized ones to
// reduce its dependencies on other classes from 31 to the maximum authorized 30 or less. This means, that this class
// should not be coupled to too many other classes (Single Responsibility Principle).
public class TransferService {
    private static final Pattern SHARPS_REGEX = Pattern.compile("^#(.+?)#$");
    private static final String NO_NAME = "NO_NAME";
    private final ArgoKubernetesService argoKubernetesService;
    private final JobService jobService;
    private final PipelineService pipelineService;
    private final ProjectService projectService;
    private TransferService self;

    public TransferService(
            ArgoKubernetesService argoKubernetesService,
            JobService jobService,
            PipelineService pipelineService,
            ProjectService projectService,
            @Lazy TransferService self
    ) {
        this.argoKubernetesService = argoKubernetesService;
        this.jobService = jobService;
        this.pipelineService = pipelineService;
        this.projectService = projectService;
        this.self = self;
    }

    /**
     * Setter-method for {@link TransferService#self} field.
     *
     * @param self is a new value of {@link TransferService#self} field.
     */
    public void setSelf(TransferService self) {
        this.self = self;
    }

    /**
     * Exporting jobs by ids.
     *
     * @param projectId project id
     * @param jobIds    jobs ids to export
     * @return set of jsons
     */
    private Set<JobDto> exportJobs(final String projectId, final Set<String> jobIds) {
        Set<JobDto> exportedJobs = Sets.newHashSetWithExpectedSize(jobIds.size());
        for (String jobId : jobIds) {
            exportedJobs.add(jobService.getById(projectId, jobId));
        }
        return exportedJobs;
    }

    /**
     * Exporting pipelines by ids.
     *
     * @param projectId project id
     * @param pipelines jobs ids to export
     * @return set of jsons
     */
    private Set<PipelinesWithRelatedJobs> exportPipelines(String projectId,
                                                          Set<ExportRequestDto.PipelineRequest> pipelines) {
        Set<PipelinesWithRelatedJobs> exportedPipelines = Sets.newHashSetWithExpectedSize(pipelines.size());
        for (ExportRequestDto.PipelineRequest pipeline : pipelines) {
            PipelineDto pipelineToExport = pipelineService.getById(projectId, pipeline.getPipelineId());
            PipelinesWithRelatedJobs pipelinesWithRelatedJobs = new PipelinesWithRelatedJobs(pipelineToExport);
            if (pipeline.isWithRelatedJobs()) {
                pipelinesWithRelatedJobs.getRelatedJobIds().addAll(extractJobsIdsFromPipeline(pipelineToExport));
            }
            exportedPipelines.add(pipelinesWithRelatedJobs);
        }
        return exportedPipelines;
    }

    /**
     * Appends missing project args to DTO.
     *
     * @param missingProjectArgs container for missing args
     * @param kind               flag whether it is job or pipeline
     * @param graph              graph of job or pipeline
     * @param existingArgs       existing project args
     */
    private static void appendMissingArgs(
            String name,
            Map<String, List<MissingParamDto>> missingProjectArgs,
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
                    List<MissingParamDto> missingParamDtos = missingProjectArgs.get(requiredArgs);
                    missingParamDtos.add(MissingParamDto.builder()
                            .name(name)
                            .kind(kind)
                            .nodeId(nodeIds.get(index)).build());
                    missingProjectArgs.put(requiredArgs, missingParamDtos);
                } else {
                    List<MissingParamDto> missingParamDto = new ArrayList<>();
                    missingParamDto.add(MissingParamDto.builder()
                            .name(name)
                            .kind(kind)
                            .nodeId(nodeIds.get(index)).build());
                    missingProjectArgs.put(requiredArgs, missingParamDto);
                }
            }
            index++;
        }
    }

    /**
     * Method to get all jobs IDs from pipeline.
     *
     * @param pipeline source pipeline.
     * @return all jobs IDs from pipeline.
     */
    private static List<String> extractJobsIdsFromPipeline(PipelineDto pipeline) {
        GraphDto graph = GraphUtils.parseGraph(pipeline.getDefinition());
        List<GraphDto.NodeDto> nodes = graph.getNodes();
        return nodes.stream()
                .map(node -> node.getValue().get(JOB_ID_LABEL))
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Method replaces old jobs IDs to new in pipelines.
     *
     * @param definitionJson source pipeline's definition.
     * @return processed definition.
     */
    private JsonNode replaceJobsIdsInPipeline(String projectId, JsonNode definitionJson) {
        ObjectMapper mapper = new ObjectMapper();
        DefinitionDto definitionDto = mapper.convertValue(definitionJson, DefinitionDto.class);
        definitionDto.getGraph().stream()
                .filter(StageDto::isVertex)
                .forEach((StageDto node) -> {
                    Object jobName = node.getValue().get(JOB_NAME_LABEL);
                    if (jobName != null) {
                        String jobId = jobService.getIdByName(projectId, jobName.toString());
                        if (jobId != null) {
                            node.getValue().replace(JOB_ID_LABEL, jobId);
                        } else {
                            throw new IllegalArgumentException(
                                    String.format("Cannot find job for [%s] node", node.getId()));
                        }
                    }
                });
        return mapper.valueToTree(definitionDto);
    }

    /**
     * Appends missing project args from job to DTO.
     *
     * @param job                job's DTO
     * @param missingProjectArgs container for missing args
     * @param existingArgs       existing project args
     */
    private static void appendJobsMissingArgs(JobDto job, Map<String, List<MissingParamDto>> missingProjectArgs,
                                              List<String> existingArgs) {
        GraphDto graph = GraphUtils.parseGraph(job.getDefinition());
        appendMissingArgs(job.getName(), missingProjectArgs, KIND_JOB, graph, existingArgs);
    }

    /**
     * Importing jobs.
     *
     * @param projectId projectId;
     * @param jobsToImport jobs for import
     * @param importParams import params
     */
    protected void importJobs(String projectId, List<JobDto> jobsToImport, ImportParamsDto importParams) {
        for (JobDto jobDto : jobsToImport) {
            String jobName = Optional.ofNullable(jobDto.getName())
                    .orElse(NO_NAME);
            try {
                appendJobsMissingArgs(jobDto, importParams.getMissingProjectParams(), importParams.getExistingParams());
                appendJobsMissingArgs(jobDto, importParams.getMissingProjectConnections(),
                        importParams.getExistingConnections());
                jobService.create(projectId, jobDto);
            } catch (BadRequestException | IllegalArgumentException ex) {
                LOGGER.error(ex.getMessage(), ex);
                importParams.getNotImportedJobs().add(jobName);
                importParams.getErrorsInJobs().computeIfAbsent(jobName,
                        key -> new ArrayList<>()).add(ex.getMessage());
            }
        }
    }

    /**
     * Appends missing project args from pipeline to DTO.
     *
     * @param pipelineDto        pipeline DTO
     * @param missingProjectArgs container for missing args
     * @param existingArgs       existing project args
     */
    private static void appendPipelinesMissingArgs(
            PipelineDto pipelineDto,
            Map<String, List<MissingParamDto>> missingProjectArgs,
            List<String> existingArgs) {
        GraphDto graph = GraphUtils.parseGraph(pipelineDto.getDefinition());
        appendMissingArgs(pipelineDto.getId(), missingProjectArgs, KIND_PIPELINE, graph, existingArgs);
    }

    /**
     * Importing pipelines.
     *
     * @param projectId                 project id
     * @param pipelinesToImport         pipelines for import
     * @param importParams      container for import parameters
     */
    protected void importPipelines(String projectId,
                                   List<PipelineDto> pipelinesToImport,
                                   ImportParamsDto importParams) {
        for (PipelineDto pipelineToImport : pipelinesToImport) {
            String name = Optional.ofNullable(pipelineToImport.getName())
                    .orElse(NO_NAME);
            String id = pipelineToImport.getId();
            JsonNode definition = replaceJobsIdsInPipeline(projectId, pipelineToImport.getDefinition());
            PipelineParams params = pipelineToImport.getParams();
            PipelineDto existedPipeline = null;
            if (id != null) {
                try {
                    existedPipeline = pipelineService.getById(projectId, id);
                } catch (ResourceNotFoundException e) {
                    LOGGER.info("Pipeline with id {} doesn't exist, the import procedure will create a new one: {}",
                            id, e.getLocalizedMessage());
                }
            }
            try {
                if (existedPipeline == null) {
                    pipelineService.create(projectId, name, definition, params);
                } else {
                    LOGGER.info("Pipeline with id {} will be updated via import operation", id);
                    pipelineService.update(projectId, id, name, params, definition);
                }
                appendPipelinesMissingArgs(pipelineToImport, importParams.getMissingProjectParams(),
                        importParams.getExistingParams());
                appendPipelinesMissingArgs(pipelineToImport, importParams.getMissingProjectConnections(),
                        importParams.getExistingConnections());
            } catch (BadRequestException ex) {
                LOGGER.error(ex.getMessage(), ex);
                importParams.getNotImportedPipelines().add(name);
                importParams.getErrorsInPipelines().computeIfAbsent(name, key -> new ArrayList<>())
                        .add(ex.getMessage());
            }
        }
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
        Set<PipelineDto> exportedPipelines = new HashSet<>();

        pipelinesWithRelatedJobs.forEach((PipelinesWithRelatedJobs pipelineWithRelatedJobs) -> {
            jobsWithPipelineJobsIds.addAll(pipelineWithRelatedJobs.getRelatedJobIds());
            exportedPipelines.add(pipelineWithRelatedJobs.getPipeline());
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
     * @param jobsList      jobs in json format
     * @param pipelinesList pipelines in json format
     * @return not imported pipelines and jobs ids
     */
    public ImportResponseDto importing(String projectId, List<JobDto> jobsList,
                                       List<PipelineDto> pipelinesList) {
        ImportResponseDto.ImportResponseDtoBuilder builder = ImportResponseDto.builder();
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
        ImportParamsDto importParams = ImportParamsDto.builder()
                .existingParams(existingParams)
                .existingConnections(existingConnections)
                .build();
        if (!jobsList.isEmpty()) {
            self.importJobs(projectId, jobsList, importParams);
        }
        if (!pipelinesList.isEmpty()) {
            self.importPipelines(projectId, pipelinesList, importParams);
        }
        return builder
                .missingProjectParams(importParams.getMissingProjectParams())
                .missingProjectConnections(importParams.getMissingProjectConnections())
                .notImportedJobs(importParams.getNotImportedJobs())
                .notImportedPipelines(importParams.getNotImportedPipelines())
                .errorsInJobs(importParams.getErrorsInJobs())
                .errorsInPipelines(importParams.getErrorsInPipelines())
                .build();
    }

    /**
     * Checks if user has permission to use import functionality
     *
     * @param projectId id of the project
     * @return true if user has the access
     */
    public boolean checkImportAccess(String projectId) {
        return argoKubernetesService.isAccessible(projectId, "configmaps", "", CREATE_ACTION);
    }
}
