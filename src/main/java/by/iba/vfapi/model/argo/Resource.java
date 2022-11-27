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

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resource in workflows.
 */

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class Resource implements Serializable {
    private static final long serialVersionUID = 1;
    private String action;
    private String manifest;
    private String successCondition;
    private String failureCondition;

    /**
     * Setter for resourceRequirements.
     *
     * @param action action
     * @return this
     */
    public Resource action(String action) {
        this.action = action;
        return this;
    }

    /**
     * Adding manifest item.
     *
     * @param manifest Parameter
     * @return this
     */
    public Resource manifest(String manifest) {
        this.manifest = manifest;
        return this;
    }

    /**
     * Adding successCondition.
     *
     * @param successCondition successCondition
     * @return this
     */
    public Resource successCondition(String successCondition) {
        this.successCondition = successCondition;
        return this;
    }

    /**
     * Adding failureCondition.
     *
     * @param failureCondition failureCondition
     * @return this
     */
    public Resource failureCondition(String failureCondition) {
        this.failureCondition = failureCondition;
        return this;
    }
}
