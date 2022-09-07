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
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
        final CustomNamespaceAnnotationsConfig customAnnotations,
        AuthenticationService authenticationService) {
        this.kubernetesService = kubernetesService;
        this.imagePullSecret = imagePullSecret;
        this.serviceAccount = serviceAccount;
        this.roleBinding = roleBinding;
        this.namespacePrefix = namespacePrefix;
        this.namespaceApp = namespaceApp;
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
                                                          "secrets",
                                                          "",
                                                          Constants.UPDATE_ACTION);
        return ParamsDto.fromSecret(secret).editable(editable).build();
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
}
