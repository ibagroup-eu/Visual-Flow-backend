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
package by.iba.vfapi.services;

import by.iba.vfapi.dto.projects.ConnectDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.exceptions.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Service class for manipulations with data, will be sent to DB-Service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabasesService {

    private final ProjectService projectService;

    /**
     * Method for getting the {@link ConnectDto connection object}, will be sent to db-service.
     * In addition, parses connection params and replaces them to their values.
     * @param id is the project id.
     * @param name is the connection name.
     * @return parsed connection object with filled params.
     */
    public ConnectDto getConnection(String id, String name) {
        ConnectDto connection = projectService.getConnection(id, name);
        if(connection == null) {
            return null;
        }

        return replaceParams(id, connection);
    }

    /**
     * Method for replacing keys by values for connections.
     * In addition, parses connection params and replaces them to their values.
     * @param projectId is the project id.
     * @param connection is the connection.
     * @return parsed connection object with filled params.
     */
    public ConnectDto replaceParams(String projectId, @Valid ConnectDto connection){
        List<ParamDto> params = projectService.getParams(projectId).getParams();
        Map<String, String> paramsMap = params.stream().collect(Collectors.toMap(ParamDto::getKey,
                paramDto -> paramDto.getValue().getText()));
        String conStrRepresentation = connection.getValue().toString();
        Matcher matcher = ProjectService.PARAM_MATCH_PATTERN.matcher(conStrRepresentation);
        while(matcher.find()) {
            String found = matcher.group(1);
            if(paramsMap.containsKey(found)) {
                conStrRepresentation = conStrRepresentation.replace(matcher.group(), paramsMap.get(found));
                matcher = ProjectService.PARAM_MATCH_PATTERN.matcher(conStrRepresentation);
            }
        }
        try {
            connection.setValue(new ObjectMapper().readTree(conStrRepresentation));
        } catch (JsonProcessingException e) {
            LOGGER.error("An error occurred during connection string parsing: {}", e.getLocalizedMessage());
            throw new BadRequestException("Required connection has incorrect structure!");
        }
        return connection;
    }
}
