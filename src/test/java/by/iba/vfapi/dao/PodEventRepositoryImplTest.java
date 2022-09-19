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

import by.iba.vfapi.model.PodEvent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
@Slf4j
class PodEventRepositoryImplTest {
    private PodEventRepositoryImpl podEventRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    private PodEvent podEvent;

    @BeforeEach
    void setUp() {
        podEventRepository = new PodEventRepositoryImpl(redisTemplate);
        podEventRepository.hashOperations = mock(HashOperations.class);
        podEvent = new PodEvent("test", "test", "test", "test", "test", "test");
    }

    @Test
    void testFindAll() {
        Map<String, PodEvent> expected = new HashMap<>();
        expected.put("test", podEvent);
        when(podEventRepository.hashOperations.entries(anyString())).thenReturn(expected);

        assertEquals(expected, podEventRepository.findAll("test"), "Item must be equal to expected");
    }

    @Test
    void testAdd() {
        doNothing().when(podEventRepository.hashOperations).put(anyString(), anyString(), any(PodEvent.class));
        podEventRepository.add("test", podEvent);
        verify(podEventRepository.hashOperations).put(anyString(), anyString(), any(PodEvent.class));
    }

    @Test
    void testDelete() {
        when(podEventRepository.hashOperations.delete(anyString(), anyString())).thenReturn(1L);
        podEventRepository.delete("test", "test");
        assertEquals(1L, podEventRepository.hashOperations.delete(anyString(), anyString()), "Item must be equal to expected");
    }

    @Test
    void testFindById() {
        when(podEventRepository.hashOperations.get(anyString(), anyString())).thenReturn(podEvent);

        assertEquals(podEvent, podEventRepository.findById("test", "test"), "Item must be equal to expected");
    }
}
