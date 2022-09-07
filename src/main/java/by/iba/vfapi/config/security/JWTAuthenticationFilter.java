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

package by.iba.vfapi.config.security;

import by.iba.vfapi.config.SuperusersConfig;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.KubernetesService;
import by.iba.vfapi.services.auth.AuthenticationService;
import by.iba.vfapi.services.auth.OAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter for extracting token from every request
 * and check with OAuth service.
 */
@Slf4j
@Component
public class JWTAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuthService oauthService;
    private final AuthenticationService authenticationService;
    private final KubernetesService kubernetesService;
    private final Set<String> superusers;

    @Autowired
    public JWTAuthenticationFilter(
        SuperusersConfig superusersConfig,
        OAuthService oauthService,
        AuthenticationService authenticationService,
        KubernetesService kubernetesService) {
        this.oauthService = oauthService;
        this.superusers = superusersConfig.getSet();
        this.authenticationService = authenticationService;
        this.kubernetesService = kubernetesService;
    }

    /**
     * Gets token and validates it.
     *
     * @param httpServletRequest  request.
     * @param httpServletResponse response.
     * @param filterChain         chain.
     * @throws ServletException when doFilter.
     * @throws IOException      when doFilter.
     */
    @Override
    protected void doFilterInternal(
        HttpServletRequest httpServletRequest,
        @NonNull HttpServletResponse httpServletResponse,
        @NonNull FilterChain filterChain) throws ServletException, IOException {
        LOGGER.debug("Starting authentication filter");
        try {
            String token = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
            if (token == null) {
                throw new AuthenticationServiceException("Empty token");
            } else {
                token = token.replace(BEARER_PREFIX, "");

                UserInfo userInfo = oauthService.getUserInfoByToken(token);
                userInfo.setSuperuser(superusers.contains(userInfo.getUsername()));
                if (!userInfo.hasAllInformation()) {
                    throw new BadRequestException("User information doesn't contain all necessary data");
                }

                kubernetesService.createIfNotExistServiceAccount(userInfo);
                authenticationService.setUserInfo(userInfo);

                LOGGER.info(
                    "{} has been successfully authenticated in k8s",
                    AuthenticationService.getFormattedUserInfo(userInfo));

                filterChain.doFilter(httpServletRequest, httpServletResponse);
            }
        } catch (AuthenticationException e) {
            LOGGER.error("Authentication exception", e);
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (BadRequestException e) {
            LOGGER.error("Cannot authenticate user", e);
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }
}
