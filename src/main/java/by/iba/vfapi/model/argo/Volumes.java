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
package by.iba.vfapi.model.argo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Volumes in container.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class Volumes implements Serializable {

    private static final long serialVersionUID = 1;

    private String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private PersistentVolumeClaim persistentVolumeClaim;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ConfigMap configMap;

    /**
     * Setter for name.
     *
     * @param name name
     * @return this
     */
    public Volumes name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Setter for persistentVolumeClaim.
     *
     * @param persistentVolumeClaim persistent volume claim
     * @return this
     */
    public Volumes persistentVolumeClaim(PersistentVolumeClaim persistentVolumeClaim) {
        this.persistentVolumeClaim = persistentVolumeClaim;
        return this;
    }

    /**
     * Setter for configMap.
     *
     * @param configMap configMap
     * @return this
     */
    public Volumes configMap(ConfigMap configMap) {
        this.configMap = configMap;
        return this;
    }
}