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
 * DagTask in workflows.
 */
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class DagTask implements Serializable {
    private static final long serialVersionUID = 1;
    private Arguments arguments;
    private String depends;
    private String name;
    private String template;

    /**
     * Setter for arguments.
     *
     * @param arguments Arguments
     * @return this
     */
    public DagTask arguments(Arguments arguments) {
        this.arguments = arguments;
        return this;
    }

    /**
     * Setter for dependencies.
     *
     * @param depends String
     * @return this
     */
    public DagTask depends(String depends) {
        this.depends = depends;
        return this;
    }

    /**
     * Setter for name.
     *
     * @param name name
     * @return this
     */
    public DagTask name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Setter for template.
     *
     * @param template template
     * @return this
     */
    public DagTask template(String template) {
        this.template = template;
        return this;
    }
}
