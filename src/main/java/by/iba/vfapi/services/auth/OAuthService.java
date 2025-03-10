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
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.model.auth.UserInfoBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * OAuthService class.
 * Mainly used for getting user info.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OAuthService {

    private final RestTemplate restTemplate;

    private final ApplicationConfigurationProperties appProperties;

    private final Environment env;

    /**
     * Gets user info from AppId by auth-token.
     *
     * @param token token from request.
     * @return user info object
     */
    public UserInfo getUserInfoByToken(String token) {
        LOGGER.debug("Start user info request to OAuth service");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Object> applicationRequest = new HttpEntity<>(headers);
            return UserInfoBuilder.buildWithEnv(env,
                                                restTemplate
                                                    .exchange(appProperties.getOauth().getUrl().getUserInfo(),
                                                              HttpMethod.GET,
                                                              applicationRequest,
                                                              JsonNode.class)
                                                    .getBody());
        } catch (ResourceAccessException | HttpStatusCodeException e) {
            throw new AuthenticationServiceException("Error during request", e);
        }
    }
}
