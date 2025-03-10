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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

/**
 * LogRepositoryImpl class.
 */
@Repository
public class LogRepositoryImpl implements LogRepository, InitializingBean {
    private final RedisTemplate<String, String> redisTemplate;
    protected ValueOperations<String, String> valueOperations;

    /**
     * Constructor for class LogRepositoryImpl.
     *
     * @param redisTemplate redisTemplate
     */
    @Autowired
    public LogRepositoryImpl(@Qualifier("logRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Initialize value operations after constructor.
     */
    @Override
    public void afterPropertiesSet() {
        valueOperations = redisTemplate.opsForValue();
    }

    /**
     * Get item by key.
     *
     * @param key key
     * @return object
     */
    @Override
    public String get(final String key) {
        return valueOperations.get(key);
    }

    /**
     * Set a new item.
     *
     * @param key key
     */
    @Override
    public void set(String key, String value) {
        valueOperations.set(key, value);
    }

}
