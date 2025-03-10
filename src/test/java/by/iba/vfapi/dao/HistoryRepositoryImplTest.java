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

import by.iba.vfapi.model.history.AbstractHistory;
import by.iba.vfapi.model.history.JobHistory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
class HistoryRepositoryImplTest {
    private HistoryRepositoryImpl historyRepository;
    private JobHistory history;

    @BeforeEach
    void setUp() {
        historyRepository = new HistoryRepositoryImpl();
        historyRepository.hashOperations = mock(HashOperations.class);
        history = new JobHistory("test", "test", "test",
                "test", "test", "test");
    }

    @Test
    void testFindAll() {
        Map<String, JobHistory> expected = new HashMap<>();
        expected.put("test", history);
        when(historyRepository.hashOperations.entries(anyString())).thenReturn(expected);

        assertEquals(expected, historyRepository.findAll("test"), "Item must be equal to expected");
    }

    @Test
    void testAdd() {
        doNothing().when(historyRepository.hashOperations).put(anyString(), anyString(), any(AbstractHistory.class));
        historyRepository.add("test", "test", history);
        verify(historyRepository.hashOperations).put(anyString(), anyString(), any(AbstractHistory.class));
    }

    @Test
    void testDelete() {
        when(historyRepository.hashOperations.delete(anyString(), anyString())).thenReturn(1L);
        historyRepository.delete("test", "test");
        assertEquals(1L, historyRepository.hashOperations.delete(anyString(), anyString()), "Item must be equal to expected");
    }

    @Test
    void testFindById() {
        when(historyRepository.hashOperations.get(anyString(), anyString())).thenReturn(history);

        assertEquals(history, historyRepository.findById("test", "test"), "Item must be equal to expected");
    }
}
