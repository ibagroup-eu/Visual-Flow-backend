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
import by.iba.vfapi.model.history.AbstractHistory;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;

/**
 * Configuration class for Redis.
 */
@RequiredArgsConstructor
public class RedisConfig<T> {

    protected final ApplicationConfigurationProperties appProperties;

    private final Class<T> type;

    /**
     * Getter-method for recordLogs property.
     *
     * @return recordLogs property.
     */
    public boolean isRecordLogs() {
        return appProperties.getRedis().isRecordLogs();
    }

    /**
     * Connector to database.
     *
     * @param database database index
     * @return redis connection
     */
    JedisConnectionFactory jedisConnectionFactory(int database) {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(
                appProperties.getRedis().getHost(), appProperties.getRedis().getPort()
        );
        redisStandaloneConfiguration.setDatabase(database);
        redisStandaloneConfiguration.setPassword(RedisPassword.of(appProperties.getRedis().getPassword()));

        return new JedisConnectionFactory(redisStandaloneConfiguration);
    }

    /**
     * Setting the serialization mode of RedisTemplate
     *
     * @param redisTemplate redis template
     */
    protected void setSerializer(RedisTemplate<String, T> redisTemplate) {
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<T> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(type);

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator
                .builder()
                .allowIfSubType(String.class)
                .allowIfSubType(AbstractHistory.class)
                .allowIfSubType(ArrayList.class)
                .build();
        ObjectMapper om = JsonMapper
                .builder()
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL)
                .build()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        redisTemplate.setKeySerializer(stringRedisSerializer);
        RedisSerializer<?> valueSerializer;
        if (String.class.isAssignableFrom(type)) {
            valueSerializer = stringRedisSerializer;
        } else {
            valueSerializer = jackson2JsonRedisSerializer;
        }
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();
    }

    protected RedisTemplate<String, T> createRedisTemplate(RedisConnectionFactory logRedisConnectionFactory) {
        RedisTemplate<String, T> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(logRedisConnectionFactory);
        setSerializer(redisTemplate);
        redisTemplate.setEnableTransactionSupport(true);
        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }
}
