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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * JobDefinitionRepository class.
 */
@Repository
public class JobDefinitionRepository {
    private final ValueOperations<String, JsonNode> operations;

    /**
     * Constructor for class JobDefinitionRepository.
     *
     * @param redisTemplate redisTemplate
     */
    @Autowired
    public JobDefinitionRepository(
            @Qualifier("jobDefinitionRedisTemplate") RedisTemplate<String, JsonNode> redisTemplate) {
        this.operations = redisTemplate.opsForValue();
    }

    public Optional<JsonNode> findByKey(String key) {
        JsonNode jsonNode = operations.get(key);
        return Optional.ofNullable(jsonNode);
    }

    public Optional<JsonNode> findByKeyAndUpdateExpiry(String key, long expireInSeconds) {
        JsonNode value = operations.getAndExpire(key, expireInSeconds, TimeUnit.SECONDS);
        return Optional.ofNullable(value);
    }

    public String save(String key, JsonNode value) {
        operations.set(key, value);
        return key;
    }

    public void delete(String runId) {
        operations.getOperations().delete(runId);
    }
}
