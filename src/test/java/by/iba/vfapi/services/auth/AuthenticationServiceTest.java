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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {
    private static final UserInfo USER_INFO = new UserInfo();
    private SecurityContext securityContextMock;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        securityContextMock = mock(SecurityContext.class, RETURNS_DEEP_STUBS);
        service = new AuthenticationService();
        USER_INFO.setId("id");
        USER_INFO.setEmail("email");
        USER_INFO.setName("name");
        USER_INFO.setUsername("username");
        USER_INFO.setSuperuser(true);
    }

    @Test
    void testGetUserInfo() {
        Authentication authentication = mock(Authentication.class);
        when(securityContextMock.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContextMock);
        when(securityContextMock.getAuthentication().getPrincipal()).thenReturn(USER_INFO);

        UserInfo fromContext = service.getUserInfo();
        assertEquals(USER_INFO, fromContext, "UserInfo must be equals to expected");

        verify(securityContextMock, times(2)).getAuthentication();
        verify(securityContextMock.getAuthentication()).getPrincipal();
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void testGetUserInfoAuthenticationException(boolean isNull) {
        Object returnValue = isNull ? null : "some str object";
        Authentication authentication = mock(Authentication.class);
        when(securityContextMock.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContextMock);
        when(securityContextMock.getAuthentication().getPrincipal()).thenReturn(returnValue);

        assertThrows(AuthenticationException.class, () -> service.getUserInfo(), "Expected exception must be thrown");
    }

    @Test
    void testSetUserInfo() {
        SecurityContextHolder.setContext(securityContextMock);
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
            ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        service.setUserInfo(USER_INFO);
        verify(securityContextMock).setAuthentication(captor.capture());

        UserInfo fromContext = (UserInfo) captor.getValue().getPrincipal();
        assertEquals(USER_INFO, fromContext, "UserInfo must be equals to expected");
    }
}
