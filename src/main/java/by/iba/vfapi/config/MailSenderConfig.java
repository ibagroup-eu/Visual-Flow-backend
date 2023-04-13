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

package by.iba.vfapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Mail configuration class.
 */
@Configuration
public class MailSenderConfig extends JavaMailSenderImpl {

    // Note that this constructor has the following sonar error: java:S107.
    // This error has been added to the ignore list due to the current inability to solve this problem.
    public MailSenderConfig(
            @Value("${notifications.mail.username}") final String username,
            @Value("${notifications.mail.password}") final String password,
            @Value("${notifications.mail.protocol}") final String protocol,
            @Value("${notifications.mail.host}") final String host,
            @Value("${notifications.mail.port}") final int port,
            @Value("${notifications.mail.properties.mail.smtp.starttls}") final String starttls,
            @Value("${notifications.mail.properties.mail.smtp.auth}") final String auth,
            @Value("${notifications.mail.test-connection}") final String test,
            @Value("${notifications.mail.default-encoding}") final String encoding
    ){
        super.setHost(host);
        super.setPort(port);
        super.setUsername(username);
        super.setPassword(password);
        super.setDefaultEncoding(encoding);
        Properties properties = super.getJavaMailProperties();
        properties.put("mail.transport.protocol", protocol);
        properties.put("mail.smtp.auth", auth);
        properties.put("mail.smtp.starttls.enable", starttls);
        properties.put("mail.test-connection", test);
        super.setJavaMailProperties(properties);
    }

}
