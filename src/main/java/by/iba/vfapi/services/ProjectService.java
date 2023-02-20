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

import by.iba.vfapi.common.LoadFilePodBuilder;
import by.iba.vfapi.config.CustomNamespaceAnnotationsConfig;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
import by.iba.vfapi.dto.projects.ParamDataDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ConnectionsDto;
import by.iba.vfapi.dto.projects.ProjectOverviewDto;
import by.iba.vfapi.dto.projects.ProjectOverviewListDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.dto.projects.ResourceQuotaResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.services.auth.AuthenticationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import java.util.Locale;
import javax.validation.Valid;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static by.iba.vfapi.dto.Constants.SECRETS;

/**
 * ProjectService class.
 */
@Slf4j
@Service
public class ProjectService {

    public static final Pattern PARAM_MATCH_PATTERN = Pattern.compile("#(.+?)#");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String namespacePrefix;
    private final String namespaceApp;
    private final String imagePullSecret;
    private final String serviceAccount;
    private final String roleBinding;
    private final String pvcMemory;
    private final String pvcMountPath;
    private final List<String> pvcAccessModes;
    private final CustomNamespaceAnnotationsConfig customNamespaceAnnotations;

    private final KubernetesService kubernetesService;
    private final AuthenticationService authenticationService;

    public ProjectService(
        final KubernetesService kubernetesService,
        @Value("${namespace.prefix}") final String namespacePrefix,
        @Value("${job.imagePullSecret}") final String imagePullSecret,
        @Value("${job.spark.serviceAccount}") final String serviceAccount,
        @Value("${job.spark.roleBinding}") final String roleBinding,
        @Value("${namespace.app}") final String namespaceApp,
        @Value("${pvc.memory}") final String pvcMemory,
        @Value("${pvc.mountPath}") final String pvcMountPath,
        @Value("${pvc.accessModes}") final List<String> pvcAccessModes,
        final CustomNamespaceAnnotationsConfig customAnnotations,
        AuthenticationService authenticationService) {
        this.kubernetesService = kubernetesService;
        this.imagePullSecret = imagePullSecret;
        this.serviceAccount = serviceAccount;
        this.roleBinding = roleBinding;
        this.namespacePrefix = namespacePrefix;
        this.namespaceApp = namespaceApp;
        this.pvcMemory = pvcMemory;
        this.pvcMountPath = pvcMountPath;
        this.pvcAccessModes = pvcAccessModes;
        this.customNamespaceAnnotations = customAnnotations;
        this.authenticationService = authenticationService;
    }

    private String withNamespacePrefix(final String name) {
        return namespacePrefix + name;
    }

    /**
     * Creates project.
     *
     * @param projectDto project transfer object.
     */
    public String create(@Valid final ProjectRequestDto projectDto) {
        String id = withNamespacePrefix(K8sUtils.getValidK8sName(projectDto.getName()));
        ResourceQuota limits = projectDto.getLimits().toResourceQuota().build();
        Namespace project =
                projectDto.toBuilder().build().toNamespace(id, customNamespaceAnnotations.getAnnotations()).build();
        kubernetesService.createNamespace(project);
        kubernetesService.createOrReplaceResourceQuota(id, limits);
        kubernetesService.createOrReplaceSecret(id, ParamsDto.builder().build().toSecret().build());
        kubernetesService.createOrReplaceSecret(id, ConnectionsDto.builder().build().toSecret().build());
        kubernetesService.createServiceAccount(id,
                new ServiceAccountBuilder(kubernetesService.getServiceAccount(
                        namespaceApp,
                        serviceAccount))
                        .withNewMetadata()
                        .withNamespace(id)
                        .withName(serviceAccount)
                        .endMetadata()
                        .build());
        kubernetesService.createOrReplaceSecret(id,
                new SecretBuilder(kubernetesService.getSecret(namespaceApp,
                        imagePullSecret))
                        .withNewMetadata()
                        .withNamespace(id)
                        .withName(imagePullSecret)
                        .endMetadata()
                        .build());
        kubernetesService.createRoleBinding(id,
                new RoleBindingBuilder(kubernetesService.getRoleBinding(namespaceApp,
                        roleBinding))
                        .editSubject(0)
                        .withNamespace(id)
                        .endSubject()
                        .withNewMetadata()
                        .withName(roleBinding)
                        .withNamespace(id)
                        .endMetadata()
                        .build());
        kubernetesService.createPVC(id, initializePVC());
        kubernetesService.createPod(id, LoadFilePodBuilder.getLoadFilePod(
                id,
                LoadFilePodBuilder.getBufferPVCPodParams(),
                pvcMountPath,
                imagePullSecret));
        LOGGER.info("Project {} successfully created", id);
        return id;
    }

    /**
     * Gets project by id.
     *
     * @param id project id.
     * @return project transfer object.
     */
    public ProjectResponseDto get(final String id) {
        Namespace namespace = kubernetesService.getNamespace(id);
        boolean isNamespaceEditable =
                kubernetesService.isAccessible(id, "namespaces", "", Constants.UPDATE_ACTION);
        namespace.getMetadata().setName(id);
        ResourceQuota resourceQuota = kubernetesService.getResourceQuota(id, Constants.QUOTA_NAME);
        boolean isResourceQuotaEditable =
                kubernetesService.isAccessible(id, "resourcequotas", "", Constants.UPDATE_ACTION);
        return ProjectResponseDto
                .fromNamespace(namespace)
                .editable(isNamespaceEditable)
                .limits(ResourceQuotaResponseDto
                        .limitsFromResourceQuota(resourceQuota)
                        .editable(isResourceQuotaEditable)
                        .build())
                .usage(ResourceQuotaResponseDto.usageFromResourceQuota(resourceQuota).build())
                .build();
    }

    /**
     * Gets all project names.
     *
     * @return list of project names.
     */
    public ProjectOverviewListDto getAll() {
        return ProjectOverviewListDto
                .builder()
                .projects(kubernetesService
                        .getNamespaces()
                        .stream()
                        .filter((Namespace namespace) -> namespace
                                .getMetadata()
                                .getName()
                                .startsWith(namespacePrefix) && "Active".equals(namespace.getStatus().getPhase()))
                        .map(namespace -> ProjectOverviewDto
                                .fromNamespace(namespace, !kubernetesService.isViewable(namespace)))
                        .collect(Collectors.toList()))
                .editable(authenticationService.getUserInfo().isSuperuser())
                .build();
    }

    /**
     * Updates project.
     *
     * @param id         project id.
     * @param projectDto new project params.
     */
    public void update(final String id, @Valid final ProjectRequestDto projectDto) {
        kubernetesService.editDescription(id, projectDto.getDescription());
        kubernetesService.createOrReplaceResourceQuota(id, projectDto.getLimits().toResourceQuota().build());
    }

    /**
     * Deletes project by id.
     *
     * @param id project id.
     */
    public void delete(final String id) {
        kubernetesService.deleteNamespace(id);
    }

    /**
     * Gets project resource usage.
     *
     * @param id project id.
     * @return utilization object.
     */
    public ResourceUsageDto getUsage(final String id) {
        return ResourceUsageDto
                .usageFromMetricsAndQuota(kubernetesService.topPod(id),
                        kubernetesService.getResourceQuota(id, Constants.QUOTA_NAME))
                .build();
    }

    /**
     * Creates or updates project parameters. Find params are needed to delete and determines, if
     * params have usages in other objects or not. If params have usages, does not delete them.
     *
     * @deprecated
     * @param projectId           project id.
     * @param paramDtoList list of params.
     * @return params are forbidden to delete.
     */
    @Deprecated
    public List<ParamDto> updateParams(final String projectId, @Valid final List<ParamDto> paramDtoList) {
        List<ParamDto> existingParams = getParams(projectId).getParams();

        // Replace empty usages from UI with filled ones from BE.
        for(ParamDto paramFromUI : paramDtoList) {
            for(ParamDto paramFromBE : existingParams) {
                if(paramFromUI.getKey().equals(paramFromBE.getKey())) {
                    paramFromUI.getValue().setConUsages(paramFromBE.getValue().getConUsages());
                    paramFromUI.getValue().setJobUsages(paramFromBE.getValue().getJobUsages());
                    paramFromUI.getValue().setPipUsages(paramFromBE.getValue().getPipUsages());
                }
            }
        }

        // Look for deleted params and check, if any param has usages.
        List<String> allIdsFromUI = paramDtoList.stream().map(ParamDto::getKey).collect(Collectors.toList());
        List<ParamDto> forbidToDelete = existingParams.stream()
                .filter(param -> !allIdsFromUI.contains(param.getKey()))
                .filter(param -> !param.getValue().getConUsages().isEmpty() ||
                        !param.getValue().getJobUsages().isEmpty() ||
                        !param.getValue().getPipUsages().isEmpty())
                .collect(Collectors.toList());
        paramDtoList.addAll(forbidToDelete);

        updateParamsWithoutProcessing(projectId, paramDtoList);

        LOGGER.info(
                "{} - Params for project '{}' successfully updated",
                AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()),
                projectId);
        return forbidToDelete;
    }

    /**
     * Only updates existing params
     *
     * @param id           project id.
     * @param paramDtoList list of params.
     */
    public void updateParamsWithoutProcessing(final String id, @Valid final List<ParamDto> paramDtoList) {
        kubernetesService.createOrReplaceSecret(id,
                ParamsDto
                        .builder()
                        .params(paramDtoList)
                        .build()
                        .toSecret()
                        .build());
    }

    /**
     * Gets project parameters.
     *
     * @param id project id.
     * @return list of project parameters.
     */
    public ParamsDto getParams(final String id) {
        Secret secret = kubernetesService.getSecret(id, ParamsDto.SECRET_NAME);
        boolean editable = kubernetesService.isAccessible(secret.getMetadata().getNamespace(),
                SECRETS,
                "",
                Constants.UPDATE_ACTION);
        return ParamsDto.fromSecret(secret).editable(editable).build();
    }

    /**
     * Method for creating a new parameter.
     * @param projectId is an id of the project, in which the new parameter is added.
     * @param paramKey is the new parameter key.
     * @param paramDto is the DTO object of the new parameter.
     * @return the DTO object of the new parameter.
     */
    public ParamDto createParam(String projectId, String paramKey, @Valid ParamDto paramDto) {
        List<ParamDto> existingParams = getParams(projectId).getParams();
        if (existingParams.stream().anyMatch(param -> param.getKey().equals(paramKey))){
            throw new BadRequestException("Param with that key already exists.");
        }
        existingParams.add(paramDto);
        updateParamsWithoutProcessing(projectId, existingParams);
        return paramDto;
    }

    /**
     * Method for updating a parameter.
     * @param projectId is an id of the project, in which a parameter is updated.
     * @param paramKey is the parameter key.
     * @param newParamDto is the updated version of DTO of the parameter.
     * @return the DTO object of the updated parameter.
     */
    public ParamDto updateParam(String projectId, String paramKey, @Valid ParamDto newParamDto) {
        List<ParamDto> existingParams = getParams(projectId).getParams();
        if (existingParams.stream().noneMatch(paramDto -> paramDto.getKey().equals(paramKey))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no param with that name to update.");
        }
        String newParamKey = newParamDto.getKey();
        if (!paramKey.equals(newParamKey) &&
                existingParams.stream().anyMatch(paramDto -> paramDto.getKey().equals(newParamKey))){
            throw new BadRequestException("Param with that key already exists.");
        }

        for(ParamDto paramDto : existingParams) {
            if(paramDto.getKey().equals(paramKey)) {
                paramDto.setKey(newParamDto.getKey());
                paramDto.getValue().setText(newParamDto.getValue().getText());
                paramDto.setSecret(newParamDto.isSecret());
                break;
            }
        }

        updateParamsWithoutProcessing(projectId, existingParams);
        return newParamDto;
    }

    /**
     * Method for deletion a parameter.
     * @param projectId is an id of the project, in which a parameter should be deleted.
     * @param paramKey is the parameter key.
     */
    public void deleteParam(String projectId, String paramKey) {
        List<ParamDto> existingParams = getParams(projectId).getParams();
        if (existingParams.stream().noneMatch(paramDto -> paramDto.getKey().equals(paramKey))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no param with that name to delete.");
        }
        List<ParamDto> newParamList = new LinkedList<>();
        for(ParamDto paramDto : existingParams) {
            if(!paramDto.getKey().equals(paramKey)) {
                newParamList.add(paramDto);
            } else {
                ParamDataDto using = paramDto.getValue();
                if(!using.getConUsages().isEmpty() || !using.getJobUsages().isEmpty() ||
                        !using.getPipUsages().isEmpty()) {
                    throw new BadRequestException(
                            "The param cannot be deleted due to its use in connections, jobs or pipelines.");
                }
            }
        }

        updateParamsWithoutProcessing(projectId, newParamList);
    }

    /**
     * Utility method, used for searching for all params in the string.
     * @param textToFind the string in which the search will be performed
     * @return all params from the string.
     */
    public static List<String> findParamsKeysInString(String textToFind) {
        Set<String> params = new HashSet<>();
        Matcher matcher = PARAM_MATCH_PATTERN.matcher(textToFind);
        while(matcher.find()) {
            params.add(matcher.group(1));
        }
        return new ArrayList<>(params);
    }

    /**
     * Gets project connections.
     *
     * @param id project id.
     * @return list of project connections.
     */
    public ConnectionsDto getConnections(final String id) {
        Secret secret;
        try {
            secret = kubernetesService.getSecret(id, ConnectionsDto.SECRET_NAME);
        } catch (KubernetesClientException | ResourceNotFoundException e) {
            LOGGER.error("An error occurred during getting project info: {}", e.getLocalizedMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The project has not been found!");
        }

        boolean editable = kubernetesService.isAccessible(secret.getMetadata().getNamespace(),
                SECRETS,
                "",
                Constants.UPDATE_ACTION);
        return ConnectionsDto.fromSecret(secret).editable(editable).build();
    }

    /**
     * Creates or updates project connections.
     * @deprecated
     * @param id           project id.
     * @param connectionDtoList list of connections.
     */
    public void updateConnections(final String id, @Valid final List<ConnectDto> connectionDtoList) {
        kubernetesService.createOrReplaceSecret(id,
                ConnectionsDto
                        .builder()
                        .connections(connectionDtoList)
                        .build()
                        .toSecret()
                        .build());
    }

    /**
     * Gets a connection by name.
     *
     * @param id   project id.
     * @param name name of connection.
     * @return project connection.
     */
    public ConnectDto getConnection(final String id, final String name) {
        return getConnections(id)
                .getConnections()
                .stream()
                .filter((ConnectDto cdto) -> cdto.getKey().equals(name)).findFirst().orElse(null);
    }


    /**
     * Creates a new connection.
     *
     * @param id         project id.
     * @param name       connection's name.
     * @param connectDto list of connections.
     */
    public ConnectDto createConnection(final String id, final String name, @Valid final ConnectDto connectDto) {
        List<ConnectDto> connectionsList = getConnections(id).getConnections();

        if (connectionsList.stream().anyMatch(cdto -> cdto.getKey().equals(name))){
            throw new BadRequestException("Connection with that name already exists.");
        }
        List<String> foundParams = findParamsKeysInString(connectDto.getValue().toString());
        if(!foundParams.isEmpty()) {
            List<ParamDto> existingParams = getParams(id).getParams();
            existingParams
                    .stream()
                    .filter(param -> foundParams.contains(param.getKey()))
                    .forEach(param -> param.getValue().getConUsages().add(name));
            updateParamsWithoutProcessing(id, existingParams);
        }
        connectionsList.add(connectDto);

        kubernetesService.createOrReplaceSecret(id,
                ConnectionsDto
                        .builder()
                        .connections(connectionsList)
                        .build()
                        .toSecret()
                        .build());
        return connectDto;
    }

    /**
     * Updates an existing connection.
     *
     * @param id         project id.
     * @param name       connection's name.
     * @param connectDto list of connections.
     */
    public ConnectDto updateConnection(final String id, final String name, @Valid final ConnectDto connectDto) {
        List<ConnectDto> connectionsList = getConnections(id).getConnections();

        if (connectionsList.stream().noneMatch(cdto -> cdto.getKey().equals(name))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no connection with that name to update.");
        }

        List<ConnectDto> newConnectionsList = connectionsList
                .stream()
                .filter(cdto -> !cdto.getKey().equals(name))
                .collect(Collectors.toList());

        List<String> foundParams = findParamsKeysInString(connectDto.getValue().toString());
        List<ParamDto> existingParams = getParams(id).getParams();
        for(ParamDto param : existingParams) {
            Set<String> conUsages = param.getValue().getConUsages();
            if(foundParams.contains(param.getKey())) {
                conUsages.remove(name);
                conUsages.add(connectDto.getKey());
            } else {
                conUsages.remove(name);
            }
        }
        updateParamsWithoutProcessing(id, existingParams);

        newConnectionsList.add(connectDto);

        kubernetesService.createOrReplaceSecret(id,
                ConnectionsDto
                        .builder()
                        .connections(newConnectionsList)
                        .build()
                        .toSecret()
                        .build());
        return connectDto;
    }

    /**
     * Delete a connection.
     *
     * @param projectId   project id.
     * @param name connection's name.
     */
    public void deleteConnection(final String projectId, final String name) {
        List<ConnectDto> connectionsList = getConnections(projectId).getConnections();
        if (connectionsList.stream().noneMatch(cdto -> cdto.getKey().equals(name))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no connection with that name to delete.");
        }

        List<ConnectDto> newConnectionsList = new LinkedList<>();
        for(ConnectDto connectDto: connectionsList) {
            if(!connectDto.getKey().equals(name)) {
                newConnectionsList.add(connectDto);
            } else {
                if (connectDto.getValue().get(Constants.DEPENDENT_JOB_IDS) != null &&
                        !connectDto.getValue().get(Constants.DEPENDENT_JOB_IDS).isEmpty()) {
                    throw new BadRequestException("The connection cannot be removed. There are dependencies.");
                }
                if(!findParamsKeysInString(connectDto.getValue().toString()).isEmpty()) {
                    List<ParamDto> existingParams = getParams(projectId).getParams();
                    existingParams.forEach(param -> param.getValue().getConUsages().remove(name));
                    updateParamsWithoutProcessing(projectId, existingParams);
                }
            }
        }

        kubernetesService.createOrReplaceSecret(projectId,
                ConnectionsDto
                        .builder()
                        .connections(newConnectionsList)
                        .build()
                        .toSecret()
                        .build());
    }

    /**
     * Update connection with a dependent Ids
     *
     * @param projectId projectId
     * @param connectionName connectionName
     * @param func function for updating job ids
     */
    private void updateConnectionDependency(String projectId, String connectionName,
                                            Function<ConnectDto, Set<String>> func) {

        ConnectDto connectDto = getConnection(projectId, connectionName);

        JsonNode node = connectDto.getValue();
        Set<String> dependsJobIds = func.apply(connectDto);
        ((ObjectNode) node).set(Constants.DEPENDENT_JOB_IDS, MAPPER.convertValue(dependsJobIds, JsonNode.class));
        updateConnection(projectId, connectionName,
                ConnectDto.builder()
                        .key(connectionName)
                        .value(node)
                        .build());
    }

    /**
     * Retrieve list connection names from job's stages
     *
     * @param graph graph
     * @return Set<String> List of job IDs without duplicates
     */
    private static Set<String> getConnectionIdsByDefinition(JsonNode graph) {
        try {
            return GraphDto.parseGraph(Objects.requireNonNullElse(graph,
                            MAPPER.readTree("{\"graph\":[]}")))
                    .getNodes()
                    .stream()
                    .filter(e -> e.getValue().containsKey(Constants.CONNECTION_NAME))
                    .map(k -> k.getValue().get(Constants.CONNECTION_NAME))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (JsonProcessingException e) {
            LOGGER.error("An error occurred while parsing: {}", e.getLocalizedMessage());
            throw new BadRequestException("Required connection has incorrect structure");
        }
    }

    /**
     * Comparison of stages to identify connections that were used
     *
     * @param projectId    projectId
     * @param jobId        jobId
     * @param oldDefinition old definition for job
     * @param newDefinition definition for job with updates
     */
    public void checkConnectionDependencies(String projectId, String jobId,
                                            JsonNode oldDefinition,
                                            JsonNode newDefinition) {

        Set<String> oldIds = getConnectionIdsByDefinition(oldDefinition);
        Set<String> newIds = getConnectionIdsByDefinition(newDefinition);

        newIds.stream()
                .filter(element -> !oldIds.contains(element))
                .forEach((String connectionName) ->
                        updateConnectionDependency(projectId, connectionName,
                                (ConnectDto connectDto) -> {
                                    Set<String> hashSet = new HashSet<>();
                                    if(connectDto.getValue().get(Constants.DEPENDENT_JOB_IDS) != null){
                                        connectDto
                                                .getValue()
                                                .get(Constants.DEPENDENT_JOB_IDS)
                                                .forEach(jobIds -> hashSet.add(jobIds.asText()));
                                    }
                                    hashSet.add(jobId);
                                    LOGGER.info(
                                            "Job dependency {} has been added to the connection {}",
                                            jobId,
                                            connectionName);
                                    return hashSet;
                                })
                );

        oldIds.stream()
                .filter(element -> !newIds.contains(element))
                .forEach((String connectionName) ->
                        updateConnectionDependency(projectId, connectionName,
                                (ConnectDto connectDto) -> {
                                    Set<String> hashSet = new HashSet<>();
                                    if(connectDto.getValue().get(Constants.DEPENDENT_JOB_IDS) != null) {
                                        JsonNode valuesNode = connectDto
                                                .getValue()
                                                .get(Constants.DEPENDENT_JOB_IDS);
                                        for (JsonNode valueNode : valuesNode) {
                                            hashSet.add(valueNode.asText());
                                        }
                                    }
                                    hashSet = hashSet.stream()
                                            .filter(jobIds -> !jobIds.equals(jobId))
                                            .collect(Collectors.toSet());
                                    LOGGER.info(
                                            "Job dependency {} has been removed from connection {}",
                                            jobId,
                                            connectionName);
                                    return hashSet;
                                })
                );
    }

    /**
     * Creates access table for project.
     *
     * @param id          project id.
     * @param accessTable access table.
     */
    public void createAccessTable(final String id, final Map<String, String> accessTable, final String user) {
        List<RoleBinding> oldGrants = kubernetesService.getRoleBindings(id);
        Map<String, String> oldGrantsMap = AccessTableDto.fromRoleBindings(oldGrants).build().getGrants();

        if (!Objects.equals(oldGrantsMap.get(user), accessTable.get(user))) {
            throw new BadRequestException("You cannot change your role");
        }

        List<RoleBinding> newGrants = AccessTableDto.builder().grants(accessTable).build().toRoleBindings();
        List<String> newGrantNames =
                newGrants.stream().map(RoleBinding::getMetadata).map(ObjectMeta::getName).collect(Collectors.toList());

        List<RoleBinding> grantsToRevoke = oldGrants
                .stream()
                .filter((RoleBinding rb) -> !newGrantNames.contains(rb.getMetadata().getName()) &&
                        !rb.getMetadata().getName().toLowerCase(Locale.getDefault())
                                .contains(user.toLowerCase(Locale.getDefault())))
                .collect(Collectors.toList());
        kubernetesService.deleteRoleBindings(id, grantsToRevoke);

        List<String> oldGrantNames =
                oldGrants.stream().map(RoleBinding::getMetadata).map(ObjectMeta::getName).collect(Collectors.toList());

        List<RoleBinding> grantsToApply = newGrants
                .stream()
                .filter((RoleBinding rb) -> !oldGrantNames.contains(rb.getMetadata().getName()) &&
                        !rb.getMetadata().getName().toLowerCase(Locale.getDefault())
                                .contains(user.toLowerCase(Locale.getDefault())))
                .collect(Collectors.toList());
        kubernetesService.createRoleBindings(id, grantsToApply);
    }

    /**
     * Gets access table for given project.
     *
     * @param id project id.
     * @return access table.
     */
    public AccessTableDto getAccessTable(final String id) {
        List<RoleBinding> roleBindings = kubernetesService.getRoleBindings(id);
        boolean isEditable = kubernetesService.isAccessible(id,
                "rolebindings",
                "rbac.authorization.k8s.io",
                Constants.UPDATE_ACTION);
        return AccessTableDto.fromRoleBindings(roleBindings).editable(isEditable).build();
    }

    /**
     * Initialize PVC for storing files.
     *
     * @return persistentVolumeClaim.
     */
    private PersistentVolumeClaim initializePVC() {
        return new PersistentVolumeClaimBuilder()
                .withNewMetadata().withName(K8sUtils.PVC_NAME).endMetadata()
                .withNewSpec()
                .withAccessModes(this.pvcAccessModes)
                .withNewResources()
                .addToRequests("storage", new Quantity(pvcMemory))
                .endResources()
                .endSpec()
                .build();
    }

    /**
     * Method for recalculation all params usages in all connections.
     *
     * @param projectId is a project id.
     * @return true, if there were not any errors during the recalculation.
     */
    public boolean recalculateParamsConUsages(final String projectId) {
        List<ParamDto> allParams = getParams(projectId).getParams();
        allParams.forEach(param -> param.getValue().getConUsages().clear());
        List<ConnectDto> allConnections = getConnections(projectId).getConnections();
        for(ConnectDto connectDto: allConnections) {
            List<String> foundParams = findParamsKeysInString(connectDto.getValue().toString());
            if (!foundParams.isEmpty()) {
                allParams
                        .stream()
                        .filter(param -> foundParams.contains(param.getKey()))
                        .forEach(param -> param.getValue().getConUsages().add(connectDto.getKey()));
            }
        }
        updateParamsWithoutProcessing(projectId, allParams);
        return true;
    }

}
