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

package by.iba.vfapi.model.history;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Data model of the job's history information.
 */
@NoArgsConstructor
@Getter
@Setter
@ToString(callSuper=true)
public class JobHistory extends AbstractHistory {
    private static final long serialVersionUID = 1;
    private String type;
    private String startedBy;

    /**
     * Constructor for class JobHistory.
     *
     * @param id job id
     * @param type type
     * @param startedAt time of the start
     * @param finishedAt time of the finish
     * @param startedBy username
     * @param status status of the job's run
     */
    public JobHistory(
        String id,
        String type,
        String startedAt,
        String finishedAt,
        String startedBy,
        String status) {
        super(id, startedAt, finishedAt, status);
        this.type = type;
        this.startedBy = startedBy;
    }
}
