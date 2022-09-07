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

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Resource usage DTO class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "DTO with percentages of used resources")
public class ResourceUsageDto {
    @Schema(description = "Percentages of used cpu resources", example = "0.5")
    private float cpu;
    @Schema(description = "Percentages of used RAM", example = "0.64")
    private float memory;

    /**
     * Creating ResourceUsageDtoBuilder from mods metrics and quota.
     *
     * @param metrics list of PodMetric
     * @param quota   ResourceQuota
     * @return ResourceUsageDtoBuilder
     */
    public static ResourceUsageDtoBuilder usageFromMetricsAndQuota(
        Collection<PodMetrics> metrics, ResourceQuota quota) {
        BigDecimal limitsCpu = Quantity.getAmountInBytes(quota.getStatus().getHard().get(Constants.LIMITS_CPU));
        BigDecimal limitsMemory =
            Quantity.getAmountInBytes(quota.getStatus().getHard().get(Constants.LIMITS_MEMORY));
        return ResourceUsageDto
            .builder()
            .cpu(accumulateByMetricType(metrics, Constants.CPU_FIELD)
                     .divide(limitsCpu, Constants.USAGE_ACCURACY, RoundingMode.UP)
                     .floatValue())
            .memory(accumulateByMetricType(metrics, Constants.MEMORY_FIELD)
                        .divide(limitsMemory, Constants.USAGE_ACCURACY, RoundingMode.UP)
                        .floatValue());
    }

    /**
     * Calculate metric by type.
     *
     * @param metrics Collection of PodMetrics
     * @param type    type
     * @return values from metric
     */
    private static BigDecimal accumulateByMetricType(Collection<PodMetrics> metrics, String type) {
        return metrics
            .stream()
            .map((PodMetrics podMetrics) -> podMetrics
                .getContainers()
                .stream()
                .map((ContainerMetrics cm) -> Quantity.getAmountInBytes(cm.getUsage().get(type)))
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO))
            .reduce(BigDecimal::add)
            .orElse(BigDecimal.ZERO);
    }
}
