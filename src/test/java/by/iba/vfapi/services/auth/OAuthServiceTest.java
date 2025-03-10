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

import by.iba.vfapi.config.ApplicationConfigurationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(value = ApplicationConfigurationProperties.class)
class OAuthServiceTest {
    private static final String AUTH_ID = "id";
    private static final String AUTH_USERNAME = "username";
    private static final String AUTH_NAME = "name";
    private static final String AUTH_EMAIL = "email";
    @Mock
    private RestTemplate restTemplateMock;
    @Autowired
    private ApplicationConfigurationProperties appProperties;
    private OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        oAuthService = new OAuthService(restTemplateMock,
                appProperties,
                new MockEnvironment()
                        .withProperty("auth.id", AUTH_ID)
                        .withProperty("auth.username", AUTH_USERNAME)
                        .withProperty("auth.name", AUTH_NAME)
                        .withProperty("auth.email", AUTH_EMAIL));
    }

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
        assertEquals("https://basepath/api/v4/user", uriCaptor.getValue(), "Value must be equals to expected");
        assertEquals(HttpMethod.GET, methodCaptor.getValue(), "Value must be equals to expected");
        assertEquals("Bearer token",
                Objects.requireNonNull(entityCaptor.getValue().getHeaders().get("Authorization")).get(0),
                "Headers must be equal to expected");
        assertEquals(JsonNode.class, jsonCaptor.getValue(), "Argument should have JsonNode type");
    }
}
