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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Class represents spec for custom resource WorkflowTemplate.
 */
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowTemplateSpec implements Serializable {
    private static final long serialVersionUID = 1;

    private String entrypoint;
    private List<Template> templates;
    private String serviceAccountName;
    private List<ImagePullSecret> imagePullSecrets;

    /**
     * Setter for entrypoint.
     *
     * @param entrypoint entrypoint
     * @return this
     */
    public WorkflowTemplateSpec entrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
        return this;
    }

    /**
     * Setter for templates.
     *
     * @param templates list of Template
     * @return this
     */
    public WorkflowTemplateSpec templates(Collection<Template> templates) {
        if (templates != null) {
            this.templates = new ArrayList<>(templates);
        }
        return this;
    }

    /**
     * Setter for serviceAccountName.
     *
     * @param serviceAccountName serviceAccountName object
     * @return this
     */
    public WorkflowTemplateSpec serviceAccountName(String serviceAccountName) {
        this.serviceAccountName = serviceAccountName;
        return this;
    }

    /**
     * Setter for imagePullSecrets.
     *
     * @param imagePullSecrets list of ImagePullSecret
     * @return this
     */
    public WorkflowTemplateSpec imagePullSecrets(Collection<ImagePullSecret> imagePullSecrets) {
        if (imagePullSecrets != null) {
            this.imagePullSecrets = new ArrayList<>(imagePullSecrets);
        }
        return this;
    }
}
