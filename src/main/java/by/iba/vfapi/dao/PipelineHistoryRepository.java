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

import by.iba.vfapi.config.redis.PipelineRedisConfig;
import by.iba.vfapi.config.redis.RedisConfig;
import by.iba.vfapi.model.history.AbstractHistory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * PipelineHistoryRepository class.
 */
@Repository
public class PipelineHistoryRepository<T extends AbstractHistory> extends HistoryRepositoryImpl
        implements InitializingBean {
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Constructor for class PipelineHistoryRepository.
     *
     * @param redisTemplate redisTemplate
     */
    @Autowired
    public PipelineHistoryRepository(@Qualifier("pipelineRedisTemplate") RedisTemplate<String, Object> redisTemplate,
                                     PipelineRedisConfig redisConfig) {
        this.redisTemplate = redisTemplate;
        this.redisConfig = redisConfig;
    }

    /**
     * Initialize hash operations after constructor.
     */
    @Override
    public void afterPropertiesSet() {
        hashOperations = redisTemplate.opsForHash();
    }
}
