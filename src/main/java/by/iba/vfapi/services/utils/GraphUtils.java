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

import by.iba.vfapi.dto.GraphDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.experimental.UtilityClass;
import org.springframework.data.util.StreamUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class GraphUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parse definition to nodes and edges.
     *
     * @param definition json
     * @return GraphDto with nodes and edges
     */
    public static GraphDto parseGraph(JsonNode definition) {
        ArrayNode nodesArray = definition.withArray("graph");
        List<GraphDto.NodeDto> nodes = new ArrayList<>();
        List<GraphDto.EdgeDto> edges = new ArrayList<>();
        for (JsonNode node : nodesArray) {
            JsonNode isVertex = node.get("vertex");
            if (isVertex != null && isVertex.asBoolean()) {
                nodes.add(MAPPER.convertValue(node, GraphDto.NodeDto.class));
            } else {
                edges.add(MAPPER.convertValue(node, GraphDto.EdgeDto.class));
            }
        }
        return new GraphDto(nodes, edges);
    }

    public static Map<String, JsonNode> groupNodes(JsonNode arrayNode) {
        Iterator<JsonNode> elements = arrayNode.elements();
        return StreamUtils.createStreamFromIterator(elements)
                .collect(Collectors.toMap(
                        v -> v.get("id").asText(),
                        v -> v.get("value")
                ));
    }
}
