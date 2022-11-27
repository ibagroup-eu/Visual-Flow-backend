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

package by.iba.vfapi.dao;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
class LogRepositoryImplTest {
    private LogRepositoryImpl logRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        logRepository = new LogRepositoryImpl(redisTemplate);
        logRepository.valueOperations = mock(ValueOperations.class);
    }

    @Test
    void testGet() {

        String expected = "value";
        when(logRepository.valueOperations.get("test")).thenReturn(expected);

        assertEquals(expected, logRepository.get("test"), "Item must be equal to expected");
    }

    @Test
    void testAdd() {
        doNothing().when(logRepository.valueOperations).set(anyString(), anyString());
        logRepository.set("test", "value");
        verify(logRepository.valueOperations).set(anyString(), anyString());
    }

}
