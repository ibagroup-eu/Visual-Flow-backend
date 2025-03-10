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

import by.iba.vfapi.dto.ListWithOffset;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

public class ListRepository<T> {
    protected final ListOperations<String, T> operations;

    protected ListRepository(RedisTemplate<String, T> redisTemplate) {
        this.operations = redisTemplate.opsForList();
    }

    public ListWithOffset<T> findAll(String key, long offset) {
        List<T> range = operations.range(key, offset, -1);
        return ListWithOffset.from(offset, range);
    }

    public Long save(String key, T value) {
        return operations.rightPush(key, value);
    }

    public void delete(String key) {
        operations.getOperations().delete(key);
    }
}
