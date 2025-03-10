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
package by.iba.vfapi.dto.jobs;

import by.iba.vfapi.exceptions.BadRequestException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Enum for stage operations types.
 * Mainly used for check in {@link JobDto}.
 */
public enum StageType {
    READ,
    WRITE,
    VALIDATE,
    UNION,
    JOIN,
    CDC,
    TRANSFORM,
    GROUP,
    FILTER,
    REMOVE_DUPLICATES,
    SORT,
    CACHE,
    SLICE,
    WITH_COLUMN,
    PIVOT,
    DATETIME,
    STRING,
    HANDLE_NULL;

    /**
     * Converter-method. Converts string to {@link StageType}.
     * If illegal string is provided, throw {@link BadRequestException}.
     * @param operation should be the same, as the name of some {@link StageType} constant.
     * @return converted to {@link StageType} object.
     */
    public static StageType toStageType(String operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Stage operation cannot be null.");
        }
        return Arrays.stream(values())
                .filter(st -> st.name().toLowerCase(Locale.ROOT).equals(operation.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Invalid stage type."));
    }
}
