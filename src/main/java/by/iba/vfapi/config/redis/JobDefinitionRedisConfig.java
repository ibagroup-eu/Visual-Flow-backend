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

package by.iba.vfapi.config.redis;

import by.iba.vfapi.config.ApplicationConfigurationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Configuration class for JobDefinitionRedisConfig.
 */
@Configuration
public class JobDefinitionRedisConfig extends RedisConfig<JsonNode> {

    @Autowired
    public JobDefinitionRedisConfig(ApplicationConfigurationProperties appProperties) {
        super(appProperties, JsonNode.class);
    }

    /**
     * Configure redis connection factory for job's definitions.
     *
     * @return JedisConnectionFactory
     */
    @Bean
    public JedisConnectionFactory jobDefinitionRedisConnectionFactory() {
        return jedisConnectionFactory(appProperties.getRedis().getJobDefinitionDatabase());
    }

    /**
     * Configure redisTemplate injection.
     *
     * @return instance of RedisTemplate
     */
    @Bean(name = "jobDefinitionRedisTemplate")
    public RedisTemplate<String, JsonNode> jobRedisTemplate() {
        return createRedisTemplate(jobDefinitionRedisConnectionFactory());
    }
}
