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

package by.iba.vfapi.services.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthServiceTest {


    private static final String AUTH_ID = "id";
    private static final String AUTH_USERNAME = "username";
    private static final String AUTH_NAME = "name";
    private static final String AUTH_EMAIL = "email";
    private final RestTemplate restTemplateMock = mock(RestTemplate.class);
    private final OAuthService oAuthService = new OAuthService(restTemplateMock,
                                                               "uri1",
                                                               new MockEnvironment()
                                                                   .withProperty("auth.id", AUTH_ID)
                                                                   .withProperty("auth.username", AUTH_USERNAME)
                                                                   .withProperty("auth.name", AUTH_NAME)
                                                                   .withProperty("auth.email", AUTH_EMAIL));

    @Test
    void testGetUserInfoByToken() {
        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpMethod> methodCaptor = ArgumentCaptor.forClass(HttpMethod.class);
        ArgumentCaptor<HttpEntity<Object>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<Class<JsonNode>> jsonCaptor = ArgumentCaptor.forClass(Class.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode nodes = mapper.createObjectNode();
        Map
            .of(AUTH_ID, "test", AUTH_USERNAME, "tester", AUTH_NAME, "abc", AUTH_EMAIL, "test@test.com")
            .forEach((k, v) -> nodes.set(k, new TextNode(v)));

        when(restTemplateMock.exchange(uriCaptor.capture(),
                                       methodCaptor.capture(),
                                       entityCaptor.capture(),
                                       jsonCaptor.capture())).thenReturn(ResponseEntity.ok(nodes));
        oAuthService.getUserInfoByToken("token");
        assertEquals("uri1", uriCaptor.getValue(), "Value must be equals to expected");
        assertEquals(HttpMethod.GET, methodCaptor.getValue(), "Value must be equals to expected");
        assertEquals("Bearer token",
                     entityCaptor.getValue().getHeaders().get("Authorization").get(0),
                     "Headers must be equal to expected");
        assertEquals(JsonNode.class, jsonCaptor.getValue());
    }
}
