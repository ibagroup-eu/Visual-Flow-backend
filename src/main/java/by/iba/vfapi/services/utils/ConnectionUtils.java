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
package by.iba.vfapi.services.utils;

import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ConnectionsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Base64;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for operations, related to connections.
 */
@UtilityClass
public class ConnectionUtils {

    public static final String SECRET_NAME = "connections";

    /**
     * Utility method to convert {@link Secret} into {@link ConnectionsDto.ConnectionsDtoBuilder} object.
     *
     * @param secret kubernetes secret, represented as connection.
     * @return converted secret.
     */
    public static ConnectionsDto.ConnectionsDtoBuilder fromSecret(Secret secret) {
        return ConnectionsDto.builder().connections(Objects.requireNonNullElse(secret.getData(),
                        new HashMap<String, String>())
                .entrySet()
                .stream()
                .map((Map.Entry<String, String> entry) -> {
                    try {
                        return ConnectDto
                                .builder()
                                .key(entry.getKey())
                                .value(new ObjectMapper().readTree(new String(Base64.decodeBase64(entry.getValue()),
                                        StandardCharsets.UTF_8)))
                                .build();
                    } catch (JsonProcessingException e) {
                        throw new BadRequestException("Json Processing Exception", e);
                    }
                })
                .collect(Collectors.toList()));
    }

    /**
     * Utility method to convert {@link ConnectionsDto} into {@link SecretBuilder} object.
     *
     * @param connectionsDto connections list DTO.
     * @return converted connections.
     */
    public static SecretBuilder toSecret(@Valid ConnectionsDto connectionsDto) {
        Map<String, String> data = Optional
                .ofNullable(connectionsDto.getConnections())
                .orElseGet(Collections::emptyList)
                .stream()
                .collect(Collectors.toMap(ConnectDto::getKey, (ConnectDto connection) ->
                        connection.getValue().toString()));
        return new SecretBuilder()
                .addToStringData(data)
                .withNewMetadata()
                .withName(SECRET_NAME)
                .endMetadata();
    }
}
