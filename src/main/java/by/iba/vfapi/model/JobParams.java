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

package by.iba.vfapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parameters for jobs.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class JobParams implements Serializable {
    private static final long serialVersionUID = 1;
    @JsonProperty("DRIVER_CORES")
    private String driverCores;
    @JsonProperty("DRIVER_MEMORY")
    private String driverMemory;
    @JsonProperty("DRIVER_REQUEST_CORES")
    private String driverRequestCores;
    @JsonProperty("EXECUTOR_CORES")
    private String executorCores;
    @JsonProperty("EXECUTOR_INSTANCES")
    private String executorInstances;
    @JsonProperty("EXECUTOR_MEMORY")
    private String executorMemory;
    @JsonProperty("EXECUTOR_REQUEST_CORES")
    private String executorRequestCores;
    @JsonProperty("SHUFFLE_PARTITIONS")
    private String shufflePartitions;
    @JsonProperty("TAGS")
    private List<String> tags = new ArrayList<>();

    /**
     * Setter for driverCores.
     *
     * @param  driverCores driverCores
     * @return this
     */
    public JobParams driverCores(String driverCores) {
        this.driverCores = driverCores;
        return this;
    }

    /**
     * Setter for driverMemory.
     *
     * @param  driverMemory driverMemory
     * @return this
     */
    public JobParams driverMemory(String driverMemory) {
        this.driverMemory = driverMemory;
        return this;
    }

    /**
     * Setter for driverRequestCores.
     *
     * @param  driverRequestCores driverRequestCores
     * @return this
     */
    public JobParams driverRequestCores(String driverRequestCores) {
        this.driverRequestCores = driverRequestCores;
        return this;
    }

    /**
     * Setter for executorCores.
     *
     * @param  executorCores executorCores
     * @return this
     */
    public JobParams executorCores(String executorCores) {
        this.executorCores = executorCores;
        return this;
    }

    /**
     * Setter for executorInstances.
     *
     * @param  executorInstances executorInstances
     * @return this
     */
    public JobParams executorInstances(String executorInstances) {
        this.executorInstances = executorInstances;
        return this;
    }

    /**
     * Setter for executorMemory.
     *
     * @param  executorMemory executorMemory
     * @return this
     */
    public JobParams executorMemory(String executorMemory) {
        this.executorMemory = executorMemory;
        return this;
    }

    /**
     * Setter for executorRequestCores.
     *
     * @param  executorRequestCores executorRequestCores
     * @return this
     */
    public JobParams executorRequestCores(String executorRequestCores) {
        this.executorRequestCores = executorRequestCores;
        return this;
    }

    /**
     * Setter for shufflePartitions.
     *
     * @param  shufflePartitions shufflePartitions
     * @return this
     */
    public JobParams shufflePartitions(String shufflePartitions) {
        this.shufflePartitions = shufflePartitions;
        return this;
    }

    /**
     * Setter for tags.
     *
     * @param  tags tags
     * @return this
     */
    public JobParams tags(Collection<String> tags) {
        if (tags != null) {
            this.tags = new ArrayList<>(tags);
        }
        return this;
    }
}
