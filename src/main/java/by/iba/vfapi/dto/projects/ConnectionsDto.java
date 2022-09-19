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

import by.iba.vfapi.exceptions.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.codec.binary.Base64;

/**
 * Connections DTO class.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Schema(description = "DTO with list of project connections")
public class ConnectionsDto {
    public static final String SECRET_NAME = "connections";

    @Schema(description = "Whether current user can modify project connections")
    private boolean editable;
    @NotNull
    @Valid
    @ArraySchema(arraySchema = @Schema(description = "List of all connections for given project"))
    private List<ConnectDto> connections;

    public static ConnectionsDtoBuilder fromSecret(Secret secret) {
        Map<String, String> secretData = Objects.requireNonNullElse(secret.getData(), Collections.emptyMap());
        List<ConnectDto> connections = secretData
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
            .collect(Collectors.toList());
        return ConnectionsDto.builder().connections(connections);
    }

    public SecretBuilder toSecret() {
        Map<String, String> data = Optional
            .ofNullable(connections)
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
