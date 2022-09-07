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

import io.fabric8.kubernetes.api.model.ResourceRequirements;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Container in workflows.
 */
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class Container implements Serializable {
    private static final long serialVersionUID = 1;

    private String image;
    private String name;
    private List<EnvFrom> envFrom;
    private List<Env> env;
    private List<String> command;
    private List<String> args;
    private String imagePullPolicy;
    private ResourceRequirements resources;

    /**
     * Setter for image.
     *
     * @param image image
     * @return this
     */
    public Container image(String image) {
        this.image = image;
        return this;
    }

    /**
     * Setter for name.
     *
     * @param name name
     * @return this
     */
    public Container name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Setter for command.
     *
     * @param command command
     * @return this
     */
    public Container command(Collection<String> command) {
        if (command != null) {
            this.command = new ArrayList<>(command);
        }
        return this;
    }

    /**
     * Setter for command.
     *
     * @param args command
     * @return this
     */
    public Container args(Collection<String> args) {
        if (args != null) {
            this.args = new ArrayList<>(args);
        }
        return this;
    }

    /**
     * Setter for envFrom.
     *
     * @param envFrom envFrom
     * @return this
     */
    public Container envFrom(Collection<EnvFrom> envFrom) {
        if (envFrom != null) {
            this.envFrom = new ArrayList<>(envFrom);
        }
        return this;
    }

    /**
     * Setter for env.
     *
     * @param env env
     * @return this
     */
    public Container env(Collection<Env> env) {
        if (env != null) {
            this.env = new ArrayList<>(env);
        }
        return this;
    }

    /**
     * Setter for imagePullPolicy.
     *
     * @param imagePullPolicy imagePullPolicy
     * @return this
     */
    public Container imagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
        return this;
    }

    /**
     * Setter for resourceRequirements.
     *
     * @param resourceRequirements resourceRequirements
     * @return this
     */
    public Container resources(ResourceRequirements resourceRequirements) {
        this.resources = resourceRequirements;
        return this;
    }
}
