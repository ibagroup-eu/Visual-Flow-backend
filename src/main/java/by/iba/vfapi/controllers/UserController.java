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

package by.iba.vfapi.controllers;

import by.iba.vfapi.config.OpenApiConfig;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.UserService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User controller class.
 */
@Slf4j
@Tag(name = "User API", description = "Get information about app users")
@RequiredArgsConstructor
@RestController
@RequestMapping("api")
public class UserController {
    private final AuthenticationService authenticationService;
    private final UserService userService;

    /**
     * Retrieves current user information.
     *
     * @return user information.
     */
    @Operation(summary = "Get current user's information", description = "Get essential information about " +
        "current user")
    @GetMapping("/user")
    public UserInfo whoAmI() {
        LOGGER.info(
            "{} - Receiving information about current user",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()));
        return authenticationService.getUserInfo();
    }

    /**
     * Retrieves application users.
     *
     * @return application users.
     */
    @Operation(summary = "Get application users", description = "Get information about all users", responses =
        {@ApiResponse(responseCode = "200", content = @Content(schema =
        @Schema(ref = OpenApiConfig.SCHEMA_USERS)))})
    @GetMapping("/users")
    public List<Map<String, String>> getUsers() {
        LOGGER.info(
            "{} - Receiving users of application",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()));
        return userService.getUsers();
    }

    /**
     * Retrieves application roles.
     *
     * @return application roles.
     */
    @Operation(summary = "Get application roles", description = "Get all defined cluster roles", responses =
        {@ApiResponse(responseCode = "200", content = @Content(schema =
        @Schema(ref = OpenApiConfig.SCHEMA_ROLES)))})
    @GetMapping("/roles")
    public List<String> getRoles() {
        LOGGER.info(
            "{} - Receiving roles of application",
            AuthenticationService.getFormattedUserInfo(authenticationService.getUserInfo()));
        return userService.getRoleNames();
    }
}
