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

package by.iba.vfapi.model.auth;

import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConfigurationException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class UserInfoBuilder {

    private static final Pattern SPLITTER = Pattern.compile("(?<!\\\\)\\.");

    /**
     * Default constructor.
     */
    private UserInfoBuilder() {
    }

    /**
     * Create UserInfo with the help of environment.
     *
     * @param env    environment
     * @param values user related data
     * @return userInfo object
     */
    public static UserInfo buildWithEnv(Environment env, JsonNode values) {
        try {
            return defaultBuild(
                env.getRequiredProperty("auth.id"),
                env.getRequiredProperty("auth.username"),
                env.getRequiredProperty("auth.name"),
                env.getRequiredProperty("auth.email"),
                values);
        } catch (IllegalStateException e) {
            throw new ConfigurationException("Not all auth parameters are defined in the config", e);
        }
    }


    /**
     * Create UserInfo by automatically pulling the data from json node.
     * Utilizes custom property names.
     *
     * @param idParam       custom name for id property
     * @param userNameParam custom name for username property
     * @param nameParam     custom name for name property
     * @param emailParam    custom name for email property
     * @param values        json node with user data
     * @return userInfo with data
     */
    private static UserInfo defaultBuild(
        @NotNull String idParam,
        @NotNull String userNameParam,
        @NotNull String nameParam,
        @NotNull String emailParam,
        @NotNull JsonNode values) {
        List.of(idParam, userNameParam, nameParam, emailParam).forEach((String fieldName) -> {
            if (!paramExists(fieldName, values)) {
                throw new BadRequestException("Required user field is either missing or null - " + fieldName);
            }
        });
        UserInfo userInfo = new UserInfo();
        Map<String, Consumer<String>> fieldAssignment = Map.of(
            idParam,
            userInfo::setId,
            userNameParam,
            userInfo::setUsername,
            nameParam,
            userInfo::setName,
            emailParam,
            userInfo::setEmail);

        fieldAssignment.forEach((key, value) -> value.accept(getParamNode(key, values).asText()));
        return userInfo;
    }

    /**
     * Helper method to check if param exists within json tree.
     * Has support for nested params, e.g. by  personal.email it will look for email within personal object.
     * Note that parameter existence is checked in the root first, and then if it doesn't exist,
     * it will try to find it as nested param.
     * Nested params are delimited by .
     * In case . is used as a part of the property name, you should escape it with backslash \
     *
     * @param param  param name
     * @param values json with values
     * @return true if parameter exists
     */
    private static boolean paramExists(String param, JsonNode values) {
        if (values.hasNonNull(param)) {
            return true;
        }
        if (!param.contains(".")) {
            return false;
        }
        String[] split = SPLITTER.split(param);
        if (split.length == 0) {
            return false;
        }
        JsonNode currentNode = values;
        for (String part : split) {
            String propName = part.replace("\\.", ".");
            if (!currentNode.hasNonNull(propName)) {
                return false;
            }
            currentNode = currentNode.get(propName);
        }
        return !currentNode.isNull();
    }

    /**
     * Helper method to retrieve param node from json tree.
     * Has support for nested params, e.g. by  personal.email it will look for email within personal object.
     * Note that parameter existence is checked in the root first, and then if it doesn't exist,
     * it will try to find it as nested param.
     * Nested params are delimited by .
     * In case . is used as a part of the property name, you should escape it with backslash \
     * IMPORTANT: this method traverses the tree naively(assumes that the property exists)
     *
     * @param param  param name
     * @param values json with values
     * @return node
     */
    private static JsonNode getParamNode(String param, JsonNode values) {
        if (values.hasNonNull(param)) {
            return values.get(param);
        }
        String[] split = SPLITTER.split(param);
        JsonNode currentNode = values;
        for (String part : split) {
            currentNode = currentNode.get(part.replace("\\.", "."));
        }
        return currentNode;
    }
}
