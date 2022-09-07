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

package by.iba.vfapi.dto.projects;

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.exceptions.BadRequestException;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Resource quota request DTO class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "DTO to represent quota information")
public class ResourceQuotaRequestDto {
    @NotNull
    @Positive
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_LIMIT_CPU)
    private Float limitsCpu;
    @NotNull
    @Positive
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_REQUIRE_CPU)
    private Float requestsCpu;
    @NotNull
    @Positive
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_LIMIT_MEMORY)
    private Float limitsMemory;
    @NotNull
    @Positive
    @Schema(ref = OpenApiConfig.SCHEMA_PROJECT_REQUIRE_MEMORY)
    private Float requestsMemory;

    /**
     * Creating ResourceQuotaBuilder.
     *
     * @return ResourceQuotaBuilder
     */
    public ResourceQuotaBuilder toResourceQuota() {
        Quantity limitsCpuQuantity = Quantity.parse(limitsCpu.toString());
        Quantity requestsCpuQuantity = Quantity.parse(requestsCpu.toString());
        Quantity limitsMemoryQuantity = Quantity.parse(limitsMemory + Constants.GIGABYTE_QUANTITY);
        Quantity requestsMemoryQuantity = Quantity.parse(requestsMemory + Constants.GIGABYTE_QUANTITY);

        if (Quantity
            .getAmountInBytes(requestsCpuQuantity)
            .compareTo(Quantity.getAmountInBytes(limitsCpuQuantity)) > 0 ||
            Quantity
                .getAmountInBytes(requestsMemoryQuantity)
                .compareTo(Quantity.getAmountInBytes(limitsMemoryQuantity)) > 0) {
            throw new BadRequestException("Requests can't be greater than limits");
        }

        return new ResourceQuotaBuilder()
            .withNewMetadata()
            .withName(Constants.QUOTA_NAME)
            .endMetadata()
            .withNewSpec()
            .addToHard(Constants.LIMITS_CPU, limitsCpuQuantity)
            .addToHard(Constants.REQUESTS_CPU, requestsCpuQuantity)
            .addToHard(Constants.LIMITS_MEMORY, limitsMemoryQuantity)
            .addToHard(Constants.REQUESTS_MEMORY, requestsMemoryQuantity)
            .endSpec();
    }
}
