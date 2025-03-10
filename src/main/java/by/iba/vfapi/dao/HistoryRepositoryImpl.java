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

import by.iba.vfapi.config.redis.RedisConfig;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * HistoryRepositoryImpl class.
 */
@Repository
@NoArgsConstructor
public class HistoryRepositoryImpl<T> implements HistoryRepository<T> {

    protected RedisConfig redisConfig;
    protected HashOperations<String, String, T> hashOperations;

    /**
     * Get all items by key from Redis.
     *
     * @param key key
     * @return map of items
     */
    @Override
    public Map<String, T> findAll(final String key) {
        return hashOperations.entries(key);
    }

    /**
     * Add item to Redis.
     *
     * @param key     key
     * @param history history
     */
    @Override
    public void add(final String key, final String hashKey, final T history) {
        hashOperations.put(key, hashKey, history);
    }

    /**
     * Delete item from Redis.
     *
     * @param key key
     * @param id  hash key
     */
    @Override
    public void delete(final String key, final String id) {
        hashOperations.delete(key, id);
    }

    /**
     * Get item by key and hash key from Redis.
     *
     * @param key key
     * @param id  hash key
     * @return item
     */
    @Override
    public T findById(final String key, final String id) {
        return hashOperations.get(key, id);
    }

    /**
     * Check if hash key is already exists in Redis key.
     *
     * @param key     key
     * @param hashKey hash key
     * @return true/false
     */
    @Override
    public boolean hasKey(final String key, final String hashKey) {
        return hashOperations.hasKey(key, hashKey);
    }

    @Override
    public boolean recordLogs() {
        return redisConfig.isRecordLogs();
    }
}
