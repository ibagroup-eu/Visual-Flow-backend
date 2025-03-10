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

import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    public MailSenderConfig(ApplicationConfigurationProperties appProperties) {
        super.setHost(appProperties.getNotifications().getMail().getHost());
        super.setPort(appProperties.getNotifications().getMail().getPort());
        super.setUsername(appProperties.getNotifications().getMail().getUsername());
        super.setPassword(appProperties.getNotifications().getMail().getPassword());
        super.setDefaultEncoding(appProperties.getNotifications().getMail().getDefaultEncoding());
        addProperties(appProperties);
    }

    /**
     * Method for putting new properties in default email properties.
     * @param appProperties application properties in yaml.
     */
    private void addProperties(ApplicationConfigurationProperties appProperties) {
        Properties properties = super.getJavaMailProperties();
        properties.put("mail.transport.protocol", appProperties.getNotifications().getMail().getProtocol());
        properties.put("mail.smtp.auth",
                appProperties.getNotifications().getMail().getProperties().getMail().getSmtp().isAuth());
        properties.put("mail.smtp.starttls.enable",
                appProperties.getNotifications().getMail().getProperties().getMail().getSmtp().isStarttls());
        properties.put("mail.test-connection", appProperties.getNotifications().getMail().isTestConnection());
        setJavaMailProperties(properties);
    }

}
