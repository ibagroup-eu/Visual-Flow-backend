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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class Step implements Serializable {
    private static final long serialVersionUID = 1;
    private String name;
    private String template;
    private String when;

    /**
     * Setter for name.
     *
     * @param name value
     * @return this
     */
    public Step name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Setter for template.
     *
     * @param template value
     * @return this
     */
    public Step template(String template) {
        this.template = template;
        return this;
    }

    /**
     * Setter for when.
     *
     * @param when value
     * @return this
     */
    public Step when(String when) {
        this.when = when;
        return this;
    }
}
