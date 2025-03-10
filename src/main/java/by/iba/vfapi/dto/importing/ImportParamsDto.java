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
package by.iba.vfapi.dto.importing;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility DTO. It is mainly used to store information about
 * existing connections and parameters, as well as statistical data on
 * non-imported jobs and pipelines and errors during importing.
 */
@Builder
@Getter
public class ImportParamsDto {
    @Builder.Default
    private Map<String, List<MissingParamDto>> missingProjectParams = new HashMap<>();
    @Builder.Default
    private List<String> existingParams = new ArrayList<>();
    @Builder.Default
    private Map<String, List<MissingParamDto>> missingProjectConnections = new HashMap<>();
    @Builder.Default
    private List<String> existingConnections = new ArrayList<>();
    @Builder.Default
    private List<String> notImportedJobs = new ArrayList<>();
    @Builder.Default
    private List<String> notImportedPipelines = new ArrayList<>();
    @Builder.Default
    private Map<String, List<String>> errorsInJobs = new HashMap<>();
    @Builder.Default
    private Map<String, List<String>> errorsInPipelines = new HashMap<>();
}
