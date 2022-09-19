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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;

/**
 * PodEventRepositoryImpl class.
 */
@Repository
public class PodEventRepositoryImpl implements PodEventRepository, InitializingBean {
    private RedisTemplate<String, Object> redisTemplate;
    protected HashOperations<String, String, PodEvent> hashOperations;

    /**
     * Initialize hash operations after constructor.
     */
    @Override
    public void afterPropertiesSet() {
        hashOperations = redisTemplate.opsForHash();
    }

    /**
     * Constructor for class PodEventRepositoryImpl.
     *
     * @param redisTemplate redisTemplate
     */
    @Autowired
    public PodEventRepositoryImpl(RedisTemplate<String, Object> redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get all items by key from Redis.
     *
     * @param key key
     * @return map of items
     */
    @Override
    public Map<String, PodEvent> findAll(final String key) {
        return hashOperations.entries(key);
    }

    /**
     * Add item to Redis.
     *
     * @param key      key
     * @param podEvent podEvent
     */
    @Override
    public void add(final String key, final PodEvent podEvent) {
        long timeStampMillis = Instant.now().toEpochMilli();
        hashOperations.put(key, String.valueOf(timeStampMillis), podEvent);
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
    public PodEvent findById(final String key, final String id) {
        return hashOperations.get(key, id);
    }
}
