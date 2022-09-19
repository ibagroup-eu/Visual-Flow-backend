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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parameter in workflows.
 */
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineParams implements Serializable {
    private static final long serialVersionUID = 1;
    @JsonProperty("NOTIFY_FAILURE")
    private Boolean failureNotify;
    @JsonProperty("NOTIFY_SUCCESS")
    private Boolean successNotify;
    @JsonProperty("RECIPIENTS")
    private List<String> recipients;
    @JsonProperty("TAGS")
    private List<String> tags;

    /**
     * Setter for name.
     *
     * @param failureNotify failureNotify
     * @return this
     */
    public PipelineParams failureNotify(Boolean failureNotify) {
        this.failureNotify = failureNotify;
        return this;
    }

    /**
     * Setter for value.
     *
     * @param successNotify successNotify
     * @return this
     */
    public PipelineParams successNotify(Boolean successNotify) {
        this.successNotify = successNotify;
        return this;
    }

    /**
     * Setter for recipients.
     *
     * @param recipients recipients
     * @return this
     */
    public PipelineParams recipients(Collection<String> recipients) {
        if (recipients != null) {
            this.recipients = new ArrayList<>(recipients);
        }
        return this;
    }

    /**
     * Setter for tags.
     *
     * @param  tags tags
     * @return this
     */
    public PipelineParams tags(Collection<String> tags) {
        if (tags != null) {
            this.tags = new ArrayList<>(tags);
        }
        return this;
    }
}
