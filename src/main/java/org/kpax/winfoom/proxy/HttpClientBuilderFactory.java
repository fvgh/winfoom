/*
 * Copyright (c) 2020. Eugen Covaci
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.kpax.winfoom.proxy;

import org.apache.http.HttpHost;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/10/2020
 */
@Component
public class HttpClientBuilderFactory {

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private ProxyContext proxyContext;

    @Autowired
    private CustomProxyClient proxyClient;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Autowired
    private PoolingHttpClientConnectionManager connectionManager;

    HttpClientBuilder createHttpClientBuilder() {
        RequestConfig requestConfig = createRequestConfig();
        HttpClientBuilder builder = WinHttpClients.custom().setDefaultCredentialsProvider(credentialsProvider)
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(requestConfig)
                .setRoutePlanner(new DefaultProxyRoutePlanner(requestConfig.getProxy()))
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement();

        if (systemConfig.isUseSystemProperties()) {
            builder.useSystemProperties();
        }
        return builder;
    }

    RequestConfig createRequestConfig() {
        return RequestConfig.custom()
                .setProxy(new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort()))
                .setCircularRedirectsAllowed(true)
                .build();
    }

}
