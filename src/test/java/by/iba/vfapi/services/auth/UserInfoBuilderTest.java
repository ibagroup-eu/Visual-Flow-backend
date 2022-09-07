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

import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.ConfigurationException;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.model.auth.UserInfoBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInfoBuilderTest {
    private static final String AUTH_ID = "id";
    private static final String AUTH_USERNAME = "username";
    private static final String AUTH_NAME = "name";
    private static final String AUTH_EMAIL = "email";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Environment env = new MockEnvironment()
        .withProperty("auth.id", AUTH_ID)
        .withProperty("auth.username", AUTH_USERNAME)
        .withProperty("auth.name", AUTH_NAME)
        .withProperty("auth.email", AUTH_EMAIL);

    @Test
    void testMissingProperty() {
        MockEnvironment env = new MockEnvironment();
        ObjectNode objectNode = MAPPER.createObjectNode();
        assertThrows(ConfigurationException.class, () -> {
            UserInfoBuilder.buildWithEnv(env, objectNode);
        });
    }

    @Test
    void testNullValue() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        Map
            .of(AUTH_ID, "test", AUTH_USERNAME, "tester", AUTH_NAME, "abc")
            .forEach((k, v) -> objectNode.set(k, new TextNode(v)));
        objectNode.set(AUTH_EMAIL, null);
        assertThrows(BadRequestException.class, () -> {
            UserInfoBuilder.buildWithEnv(env, objectNode);
        });

    }

    @Test
    void testNormalBuild() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        Map<String, String> values =
            Map.of(AUTH_ID, "test", AUTH_USERNAME, "tester", AUTH_NAME, "abc", AUTH_EMAIL, "test@test.com");
        values.forEach((k, v) -> objectNode.set(k, new TextNode(v)));
        UserInfo userInfo = UserInfoBuilder.buildWithEnv(env, objectNode);
        assertEquals(values.get(AUTH_ID), userInfo.getId(), "Ids should be identical");
        assertEquals(values.get(AUTH_USERNAME), userInfo.getUsername(), "Usernames should be identical");
        assertEquals(values.get(AUTH_NAME), userInfo.getName(), "Names should be identical");
        assertEquals(values.get(AUTH_EMAIL), userInfo.getEmail(), "Emails should be identical");
    }

    @Test
    void testTrickyBuild() {
        ObjectNode objectNode = MAPPER.createObjectNode();
        MockEnvironment mockEnvironment = new MockEnvironment()
            .withProperty("auth.id", AUTH_ID)
            .withProperty("auth.username", AUTH_USERNAME)
            .withProperty("auth.name", "personal.name\\.")
            .withProperty("auth.email", "personal.distr.\\.email");
        Map<String, String> values = Map.of(AUTH_ID, "test", AUTH_USERNAME, "tester");
        values.forEach((k, v) -> objectNode.set(k, new TextNode(v)));
        ObjectNode distribution = MAPPER.createObjectNode();
        String email = "test@example.com";
        distribution.set(".email", TextNode.valueOf(email));
        ObjectNode personal = MAPPER.createObjectNode();
        String test_name = "test name";
        personal.set("name.", TextNode.valueOf(test_name));
        personal.set("distr", distribution);
        objectNode.set("personal", personal);
        UserInfo userInfo = UserInfoBuilder.buildWithEnv(mockEnvironment, objectNode);
        assertEquals(values.get(AUTH_ID), userInfo.getId(), "Ids should be identical");
        assertEquals(values.get(AUTH_USERNAME), userInfo.getUsername(), "Usernames should be identical");
        assertEquals(test_name, userInfo.getName(), "Names should be identical");
        assertEquals(email, userInfo.getEmail(), "Emails should be identical");
        assertTrue(userInfo.hasAllInformation(), "Should contain all information");
    }
}
