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

import by.iba.vfapi.model.auth.UserInfo;
import lombok.NoArgsConstructor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Authentication service.
 */
@NoArgsConstructor
@Service
public class AuthenticationService {
    /**
     * Extract user info from security context.
     *
     * @return user info.
     */
    public UserInfo getUserInfo() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserInfo) {
            return (UserInfo) principal;
        }
        throw new InsufficientAuthenticationException("Can't retrieve user info. Is null? " + (principal == null));
    }

    /**
     * Sets user info into security context.
     *
     * @param userInfo user info.
     */
    public void setUserInfo(UserInfo userInfo) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(userInfo, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Fetches the essential userinfo fields and formats them
     *
     * @param userInfo user information
     * @return formatted string
     */
    public static String getFormattedUserInfo(UserInfo userInfo) {
        return String.format(
            "%s(%s)%s",
            userInfo.getUsername(),
            userInfo.getEmail(),
            userInfo.isSuperuser() ? "[superuser]" : ""
        );
    }
}
