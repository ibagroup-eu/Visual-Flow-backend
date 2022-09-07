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

import by.iba.vfapi.exceptions.ConfigurationException;
import io.argoproj.workflow.apis.WorkflowServiceApi;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for common configs.
 */
@Configuration
public class AppConfig {
    private final String argoServerUrl;

    public AppConfig(@Value("${argo.serverUrl}") String argoServerUrl) {
        this.argoServerUrl = argoServerUrl;
    }

    /**
     * Kubernetes client.
     *
     * @return kubernetes client.
     */
    @Bean
    public NamespacedKubernetesClient getKubernetesClient() {
        return new DefaultKubernetesClient();
    }

    /**
     * Api instance.
     *
     * @return api instance for argo client.
     */
    @Bean
    public WorkflowServiceApi getApiInstance() {
        return new WorkflowServiceApi(io.argoproj.workflow.Configuration
                                          .getDefaultApiClient()
                                          .setBasePath(argoServerUrl));
    }


    /**
     * Insecure Rest template. Change this to verify Certificates.
     *
     * @return insecure rest template.
     * @deprecated Rewrite after add cert to cacerts.
     */
    @Bean
    @Deprecated(forRemoval = true)
    public RestTemplate getInsecureRestTemplate() {
        try {
            TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
            SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
            CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();
            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setHttpClient(httpClient);
            return new RestTemplate(requestFactory);
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new ConfigurationException("Unable to build unsecured rest template", e);
        }
    }
}
