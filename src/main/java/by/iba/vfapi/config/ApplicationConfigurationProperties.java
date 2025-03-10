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

import by.iba.vfapi.services.utils.K8sUtils;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Application Configuration (from yaml) class.
 * Represents properties from this configuration.
 */
@Data
@Component
@ConfigurationProperties
@Validated
public class ApplicationConfigurationProperties {

    @Valid
    private ServerSettings server;
    @Valid
    private DBServiceSettings dbService;
    @Valid
    private OauthSettings oauth;
    @Valid
    private NamespaceSettings namespace;
    @Valid
    private PvcSettings pvc;
    @Valid
    private JobSettings job;
    @Valid
    private ArgoSettings argo;
    @Valid
    private NotificationsSettings notifications;
    @Valid
    private RedisSettings redis;

    /**
     * Represents service settings.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerSettings {
        private String host;
        private ServletSettings servlet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServletSettings {
        private String contextPath;
    }

    /**
     * Represents oauth and user management settings.
     */
    @Data
    public static class OauthSettings {
        private OauthUrlSettings url;
        private String provider;
    }

    /**
     * Represents settings, connected with oauth URL.
     */
    @Data
    public static class OauthUrlSettings {
        private String userInfo;
    }

    /**
     * Represents namespace settings.
     */
    @Data
    public static class NamespaceSettings {
        private String app;
        private String label;
        private String prefix;
        private Map<String, String> annotations;
    }

    /**
     * Represents PVC settings.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PvcSettings {
        private String memory;
        private String mountPath;
        private String image = K8sUtils.PVC_POD_IMAGE;
        private List<String> accessModes;
    }

    /**
     * Represents job settings.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobSettings {
        private String imagePullSecret;
        private JobConfigSettings config;
        private JobSparkSettings spark;
        private Long interactiveSessionTimeout;
    }

    /**
     * Represents settings, connected with job's configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobConfigSettings {
        private String mountPath;
    }

    /**
     * Represents settings, connected with spark.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobSparkSettings {
        private String master;
        private String image;
        private String serviceAccount;
        private String roleBinding;
    }

    /**
     * Represents ARGO settings.
     */
    @Data
    public static class ArgoSettings {
        private String serverUrl;
        private ArgoLimitsSettings limits;
        private ArgoLimitsSettings requests;
        private ArgoTtlSettings ttlStrategy;
    }

    /**
     * Represents settings, connected with ARGO resources limits.
     */
    @Data
    public static class ArgoLimitsSettings {
        private Quantity cpu;
        private Quantity memory;

        @ConstructorBinding
        public ArgoLimitsSettings(String cpu, String memory) {
            this.cpu = new Quantity(cpu);
            this.memory = new Quantity(memory);
        }
    }

    /**
     * Represents settings, connected with ARGO TTL.
     */
    @Data
    public static class ArgoTtlSettings {
        private Integer secondsAfterCompletion;
        private Integer secondsAfterSuccess;
        private Integer secondsAfterFailure;
    }

    /**
     * Represents Notifications settings.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationsSettings {
        private String uiHost;
        private String image;
        private NotificationsSlackSettings slack;
        private NotificationsMailSettings mail;
    }

    /**
     * Represents settings, connected with Slack.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationsSlackSettings {
        private String token;
    }

    /**
     * Represents settings, connected with Mail.
     */
    @Data
    public static class NotificationsMailSettings {
        private String defaultEncoding;
        private String host;
        private String username;
        private String password;
        private Integer port;
        private NotificationsMailPropertiesSettings properties;
        private String protocol;
        private boolean testConnection;
    }

    /**
     * Represents settings, connected with Mail Properties.
     */
    @Data
    public static class NotificationsMailPropertiesSettings {
        private NotificationsMailPropertiesMailSettings mail;
    }

    /**
     * Represents more in-depth settings for mail.
     */
    @Data
    public static class NotificationsMailPropertiesMailSettings {
        private boolean debug;
        private NotificationsMailPropertiesMailSmtpSettings smtp;
    }

    /**
     * Represents settings, connected with SMTP.
     */
    @Data
    public static class NotificationsMailPropertiesMailSmtpSettings {
        private boolean debug;
        private boolean auth;
        private boolean starttls;
    }

    /**
     * Represents Databases connection settings.
     */
    @Data
    public static class DBServiceSettings {
        private String host;
    }

    /**
     * Represents Redis settings.
     */
    @Data
    public static class RedisSettings {
        private String host;
        private Integer port;
        private String username;
        private String password;
        private Integer database;
        private Integer jobHistoryDatabase;
        private Integer logDatabase;
        private Integer pipelineHistoryDatabase;
        private boolean recordLogs;
        private Integer jobMetadataDatabase;
        private Integer jobDefinitionDatabase;
        private Integer jobEventDatabase;
    }
}
