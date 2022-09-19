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

import by.iba.vfapi.config.CustomNamespaceAnnotationsConfig;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
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
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static by.iba.vfapi.dto.Constants.SECRETS;

/**
 * ProjectService class.
 */
@Slf4j
@Service
public class ProjectService {
    private final String namespacePrefix;
    private final String namespaceApp;
    private final String imagePullSecret;
    private final String serviceAccount;
    private final String roleBinding;
    private final String pvcMemory;
    private final String pvcMountPath;
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
        kubernetesService.createPod(id, initializePod(id, getBufferPVCPodParams()));
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
     * Creates or updates project parameters.
     *
     * @param id           project id.
     * @param paramDtoList list of params.
     */
    public void updateParams(final String id, @Valid final List<ParamDto> paramDtoList) {
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
     * Gets project connections.
     *
     * @param id project id.
     * @return list of project connections.
     */
    public ConnectionsDto getConnections(final String id) {
        Secret secret = kubernetesService.getSecret(id, ConnectionsDto.SECRET_NAME);
        boolean editable = kubernetesService.isAccessible(secret.getMetadata().getNamespace(),
                SECRETS,
                "",
                Constants.UPDATE_ACTION);
        return ConnectionsDto.fromSecret(secret).editable(editable).build();
    }

    /**
     * Creates or updates project connections.
     *
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
        Secret secret = kubernetesService.getSecret(id, ConnectionsDto.SECRET_NAME);
        boolean editable = kubernetesService.isAccessible(secret.getMetadata().getNamespace(),
                SECRETS,
                "",
                Constants.UPDATE_ACTION);

        return ConnectionsDto
                .fromSecret(secret)
                .editable(editable)
                .build()
                .getConnections()
                .stream()
                .filter((ConnectDto cdto) -> cdto.getKey().equals(name)).findFirst().orElse(null);
    }


    /**
     * Creates a new connection.
     *
     * @param id         project id.
     * @param connectDto list of connections.
     */
    public ConnectDto createConnection(final String id, final String name, @Valid final ConnectDto connectDto) {

        Secret secret = kubernetesService.getSecret(id, ConnectionsDto.SECRET_NAME);
        boolean editable = kubernetesService.isAccessible(secret.getMetadata().getNamespace(),
                SECRETS,
                "",
                Constants.UPDATE_ACTION);

        List<ConnectDto> connectionsList = new ArrayList<>(ConnectionsDto
                .fromSecret(secret)
                .editable(editable)
                .build()
                .getConnections());

        if (connectionsList.stream().anyMatch(cdto -> cdto.getKey().equals(name))){
            throw new BadRequestException("Connection with that name already exists.");
        } else {
            connectionsList.add(connectDto);

            kubernetesService.createOrReplaceSecret(id,
                    ConnectionsDto
                            .builder()
                            .connections(connectionsList)
                            .build()
                            .toSecret()
                            .build());
            return (connectDto);
        }
    }

    /**
     * Updates an existing connection.
     *
     * @param id         project id.
     * @param name       connection's name.
     * @param connectDto list of connections.
     */
    public ConnectDto updateConnection(final String id, final String name, @Valid final ConnectDto connectDto) {
        Secret secret = kubernetesService.getSecret(id, ConnectionsDto.SECRET_NAME);
        boolean editable = kubernetesService.isAccessible(secret.getMetadata().getNamespace(),
                SECRETS,
                "",
                Constants.UPDATE_ACTION);

        List<ConnectDto> connectionsList = new ArrayList<>(ConnectionsDto
                .fromSecret(secret)
                .editable(editable)
                .build()
                .getConnections());

        if (connectionsList.stream().anyMatch(cdto -> cdto.getKey().equals(name))){
            List<ConnectDto> newConnectionsList = connectionsList
                    .stream()
                    .filter(cdto -> !cdto.getKey().equals(name))
                    .collect(Collectors.toList());

            newConnectionsList.add(connectDto);

            kubernetesService.createOrReplaceSecret(id,
                    ConnectionsDto
                            .builder()
                            .connections(newConnectionsList)
                            .build()
                            .toSecret()
                            .build());
            return (connectDto);
        } else {
            throw new BadRequestException("There is no connection with that name to update.");
        }
    }

    /**
     * Delete a connection.
     *
     * @param id   project id.
     * @param name connection's name.
     */
    public void deleteConnection(final String id, final String name) {

        Secret secret = kubernetesService.getSecret(id, ConnectionsDto.SECRET_NAME);
        boolean editable = kubernetesService.isAccessible(secret.getMetadata().getNamespace(),
                SECRETS,
                "",
                Constants.UPDATE_ACTION);

        List<ConnectDto> connectionsList = ConnectionsDto
                .fromSecret(secret)
                .editable(editable)
                .build()
                .getConnections();

        if (connectionsList.stream().anyMatch(cdto -> cdto.getKey().equals(name))){
            List<ConnectDto> newConnectionsList = connectionsList
                    .stream()
                    .filter(cdto -> !cdto.getKey().equals(name))
                    .collect(Collectors.toList());

            kubernetesService.createOrReplaceSecret(id,
                    ConnectionsDto
                            .builder()
                            .connections(newConnectionsList)
                            .build()
                            .toSecret()
                            .build());
        } else {
            throw new BadRequestException("There is no connection with that name to delete.");
        }
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
                !rb.getMetadata().getName().toLowerCase().contains(user.toLowerCase()))
            .collect(Collectors.toList());
        kubernetesService.deleteRoleBindings(id, grantsToRevoke);

        List<String> oldGrantNames =
            oldGrants.stream().map(RoleBinding::getMetadata).map(ObjectMeta::getName).collect(Collectors.toList());

        List<RoleBinding> grantsToApply = newGrants
            .stream()
            .filter((RoleBinding rb) -> !oldGrantNames.contains(rb.getMetadata().getName()) &&
                !rb.getMetadata().getName().toLowerCase().contains(user.toLowerCase()))
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
     * Initialize Pod for mounting to PVC.
     *
     * @param id project id.
     * @return pod.
     */
    private Pod initializePod(String id, Map<String, String> params) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(K8sUtils.PVC_POD_NAME)
                .withNamespace(id)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(K8sUtils.PVC_POD_NAME)
                .withResources(K8sUtils.getResourceRequirements(params))
                .withCommand(
                        "/bin/sh",
                        "-c",
                        "while true; " +
                        "do echo Running buffer container for uploading/downloading files; " +
                        "sleep 100;done"
                )
                .withImage(K8sUtils.PVC_POD_IMAGE)
                .withImagePullPolicy("IfNotPresent")
                .addNewVolumeMount()
                .withName(K8sUtils.PVC_VOLUME_NAME)
                .withMountPath(pvcMountPath)
                .endVolumeMount()
                .endContainer()
                .addNewVolume()
                .withName(K8sUtils.PVC_VOLUME_NAME)
                .withNewPersistentVolumeClaim()
                .withClaimName(K8sUtils.PVC_NAME)
                .endPersistentVolumeClaim()
                .endVolume()
                .addNewImagePullSecret(imagePullSecret)
                .withRestartPolicy("Never")
                .endSpec()
                .build();
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
                .withAccessModes("ReadWriteMany")
                .withNewResources()
                .addToRequests("storage", new Quantity(pvcMemory))
                .endResources()
                .endSpec()
                .build();
    }

    /**
     * Get request/limits params for Pod to upload/download files.
     *
     * @return resource parameters.
     */
    private Map<String, String> getBufferPVCPodParams() {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.DRIVER_CORES, "300m");
        params.put(Constants.DRIVER_MEMORY, "300Mi");
        params.put(Constants.DRIVER_REQUEST_CORES, "100m");
        return params;
    }
}
