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

package by.iba.vfapi.dto.projects;

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.dto.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;


/**
 * Connection DTO class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Schema(description = "DTO that represents project connections")
public class ConnectDto {
    @NotNull
    @Pattern(regexp = Constants.CONNECTION_KEY_PATTERN)
    @Schema(description = "Connection's name", example = "TestConnection1")
    private String key;
    @NotNull
    @Schema(description = "Connection's value. A set of connections in JSON format",
            ref = OpenApiConfig.SCHEMA_CONNECTIONS_DEFINITION)
    private JsonNode value;
}
