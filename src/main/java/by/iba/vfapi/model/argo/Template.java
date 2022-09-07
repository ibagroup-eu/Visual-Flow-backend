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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Template in workflows.
 */
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Template implements Serializable {
    private static final long serialVersionUID = 1;

    private Container container;
    private DagTemplate dag;
    private TemplateMeta metadata;
    private Inputs inputs;
    private String name;
    private String podSpecPatch;

    /**
     * Setter for podSpecPatch.
     *
     * @param podSpecPatch podSpecPatch
     * @return this
     */
    public Template podSpecPatch(String podSpecPatch) {
        this.podSpecPatch = podSpecPatch;
        return this;
    }

    /**
     * Setter for metadata.
     *
     * @param metadata TemplateMeta
     * @return this
     */
    public Template metadata(TemplateMeta metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Setter for container.
     *
     * @param container Container
     * @return this
     */
    public Template container(Container container) {
        this.container = container;
        return this;
    }

    /**
     * Setter for dag.
     *
     * @param dag DagTemplate.
     * @return this
     */
    public Template dag(DagTemplate dag) {
        this.dag = dag;
        return this;
    }

    /**
     * Setter for inputs.
     *
     * @param inputs Inputs
     * @return this
     */
    public Template inputs(Inputs inputs) {
        this.inputs = inputs;
        return this;
    }

    /**
     * Setter for name.
     *
     * @param name name
     * @return this
     */
    public Template name(String name) {
        this.name = name;
        return this;
    }
}
