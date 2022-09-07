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

package by.iba.vfapi.dto;

import by.iba.vfapi.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.regex.Matcher;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Log DTO class.
 */
@EqualsAndHashCode
@Builder
@Getter
@ToString
@Schema(description = "DTO that represents a single log entry")
public class LogDto {
    public static final int TIMESTAMP_GROUP_INDEX = 1;
    public static final int LEVEL_GROUP_INDEX = 2;
    public static final int MESSAGE_GROUP_INDEX = 3;
    @Schema(format = "date-time")
    private final String timestamp;
    @Schema(ref = OpenApiConfig.SCHEMA_LOG_LEVELS)
    private final String level;
    @Schema(example = "dummy log message")
    private String message;

    /**
     * Getting LogDto object from Matcher.
     *
     * @param matcher matcher
     * @return LogDto object
     */
    public static LogDto fromMatcher(Matcher matcher) {
        if (matcher.matches()) {
            return LogDto
                .builder()
                .timestamp(matcher.group(LogDto.TIMESTAMP_GROUP_INDEX))
                .level(matcher.group(LogDto.LEVEL_GROUP_INDEX))
                .message(matcher.group(LogDto.MESSAGE_GROUP_INDEX))
                .build();
        }
        return null;
    }

    /**
     * Setter for message.
     *
     * @param message message
     * @return this
     */
    public LogDto withMessage(String message) {
        this.message = message;
        return this;
    }
}
