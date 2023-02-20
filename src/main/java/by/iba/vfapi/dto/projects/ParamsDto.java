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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.codec.binary.Base64;

/**
 * Params DTO class.
 */
@Data
@NoArgsConstructor
@Builder(toBuilder = true)
@Schema(description = "DTO with list of project params")
public class ParamsDto {
    public static final String SECRET_NAME = "secret";

    @Schema(description = "Whether current user can modify project params")
    private boolean editable;
    @NotNull
    @Valid
    @ArraySchema(arraySchema = @Schema(description = "List of all params for given project"))
    private List<ParamDto> params;

    public ParamsDto(boolean editable, List<ParamDto> params) {
        this.editable = editable;
        this.params = params;
    }

    public static ParamsDtoBuilder fromSecret(Secret secret) {
        Map<String, String> secretData = Objects.requireNonNullElse(secret.getData(), Collections.emptyMap());
        List<ParamDto> params = secretData
            .entrySet()
            .stream()
            .map((Map.Entry<String, String> entry) -> {
                try {
                    String body = new String(Base64.decodeBase64(entry.getValue()),
                            StandardCharsets.UTF_8);
                    if(!body.startsWith("{\"text\":")) {
                        body = String.format(
                                "{\"text\": \"%s\", \"conUsages\": [], \"jobUsages\": [], \"pipUsages\": []}", body);
                    }
                    return ParamDto
                            .builder()
                            .key(entry.getKey())
                            .value(new ObjectMapper().readValue(body, ParamDataDto.class))
                            .secret(Boolean.parseBoolean(secret.getMetadata().getAnnotations().get(entry.getKey())))
                            .build();
                } catch (JsonProcessingException e) {
                    throw new BadRequestException("Json Processing Exception", e);
                }
            })
            .collect(Collectors.toList());
        return ParamsDto.builder().params(params);
    }

    public SecretBuilder toSecret() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> data = Optional
            .ofNullable(params)
            .orElseGet(Collections::emptyList)
            .stream()
            .collect(Collectors.toMap(ParamDto::getKey, (ParamDto param) -> {
                try {
                    return mapper.writeValueAsString(param.getValue());
                } catch (JsonProcessingException e) {
                    throw new BadRequestException("Json Processing Exception", e);
                }
            }));
        Map<String, String> annotations = Optional
                .ofNullable(params)
                .orElseGet(Collections::emptyList)
                .stream()
                .collect(Collectors.toMap(ParamDto::getKey, (ParamDto param) -> String.valueOf(param.isSecret())));
        return new SecretBuilder()
            .addToStringData(data)
            .withNewMetadata()
            .withName(SECRET_NAME)
            .withAnnotations(annotations)
            .endMetadata();
    }
}
