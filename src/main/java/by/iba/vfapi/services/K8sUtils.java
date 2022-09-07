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
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * Util class for Kubernetes service.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class K8sUtils {
    static final Pattern NON_WORD_PATTERN = Pattern.compile("[^A-Za-z0-9]+");
    static final String APP = "app";
    static final String JOB_CONTAINER = "main";
    static final String APPLICATION_ROLE = "vf-role";
    static final Pattern LOG_PATTERN =
        Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2},\\d{3})\\s(?:\\[.+?\\s){2}(\\w+)\\s+(.+)$");
    public static final String DRAFT_STATUS = "Draft";
    public static final String SUSPENDED_STATUS = "Suspended";
    public static final String TERMINATED_STATUS = "Terminated";
    public static final String SUCCEEDED_STATUS = "Succeeded";
    public static final String STOPPED_STATUS = "Stopped";
    public static final String RUNNING_STATUS = "Running";
    public static final String PENDING_STATUS = "Pending";
    public static final String ERROR_STATUS = "Error";
    public static final String FAILED_STATUS = "Failed";
    public static final String SHUTDOWN_TERMINATE = "Terminate";
    public static final String SHUTDOWN_STOP = "Stop";
    static final String WORKFLOW_TEMPLATE_TYPE = "workflowtemplates.argoproj.io";
    static final String WORKFLOW_TYPE = "workflows.argoproj.io";
    static final String CRON_WORKFLOW_TYPE = "cronworkflows.argoproj.io";
    static final long USER_ID = 1000L;
    static final long GROUP_ID = 1000L;

    static final String CONFIGMAP = "configMap";


    public static String extractTerminatedStateField(
        PodStatus podStatus, Function<ContainerStateTerminated, String> method) {
        return podStatus
            .getContainerStatuses()
            .stream()
            .map((ContainerStatus cs) -> cs.getState().getTerminated())
            .filter(Objects::nonNull)
            .map(method)
            .max(Comparator.comparing(ZonedDateTime::parse, ZonedDateTime::compareTo))
            .orElse(null);
    }

    /**
     * Getting valid K8s name.
     *
     * @param name invalid name
     * @return valid name
     */
    static String getValidK8sName(final String name) {
        return NON_WORD_PATTERN.matcher(name).replaceAll("-").toLowerCase(Locale.getDefault());
    }

    /**
     * Getting resourceRequirements for Container.
     *
     * @param configMap configMap
     * @return resourceRequirements
     */
    static ResourceRequirements getResourceRequirements(Map<String, String> params) {
        Quantity limitMem = Quantity.parse(Quantity
                                               .getAmountInBytes(Quantity.parse(params.get(Constants.DRIVER_MEMORY)))
                                               .multiply(BigDecimal.valueOf(Constants.MEMORY_OVERHEAD_FACTOR))
                                               .toBigInteger()
                                               .toString());
        return new ResourceRequirementsBuilder()
            .addToLimits(Constants.CPU_FIELD, Quantity.parse(params.get(Constants.DRIVER_CORES)))
            .addToLimits(Constants.MEMORY_FIELD, limitMem)
            .addToRequests(Constants.CPU_FIELD, Quantity.parse(params.get(Constants.DRIVER_REQUEST_CORES)))
            .addToRequests(Constants.MEMORY_FIELD, Quantity.parse(params.get(Constants.DRIVER_MEMORY)))
            .build();
    }

    /**
     * Generate kubernetes compatible UUID where the first symbol is a guaranteed alphabetic character.
     *
     * @return UUID in string format
     */
     public static String getKubeCompatibleUUID() {
        return RandomStringUtils.randomAlphabetic(1) + UUID.randomUUID().toString().substring(1);
     }
}
