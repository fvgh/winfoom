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

package org.kpax.winfoom.proxy.client;

import org.apache.http.HttpHost;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.proxy.ProxyInfo;
import org.kpax.winfoom.proxy.conn.ConnectionPoolingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/10/2020
 */
@Component
class HttpClientBuilderFactory {

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Autowired
    private ConnectionPoolingManager connectionPoolingManager;

    HttpClientBuilder createClientBuilder(ProxyInfo proxyInfo) {
        if (proxyInfo.getType().isSocks()) {
            return createSocksClientBuilder(proxyInfo.getType().isSocks4());
        } else if (proxyInfo.getType().isHttp()) {
            return createHttpClientBuilder(proxyInfo);
        } else { // Direct case
            return createDirectClientBuilder();
        }
    }

    private HttpClientBuilder createHttpClientBuilder(ProxyInfo proxyInfo) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setProxy(new HttpHost(proxyInfo.getHost().getHostName(), proxyInfo.getHost().getPort()))
                .setCircularRedirectsAllowed(true)
                .build();
        HttpClientBuilder builder = WinHttpClients.custom().setDefaultCredentialsProvider(credentialsProvider)
                .setConnectionManager(connectionPoolingManager.getHttpConnectionManager())
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

    private HttpClientBuilder createDirectClientBuilder() {
        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(connectionPoolingManager.getHttpConnectionManager())
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCircularRedirectsAllowed(true)
                        .build())
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement();

        if (systemConfig.isUseSystemProperties()) {
            builder.useSystemProperties();
        }
        return builder;
    }

    private HttpClientBuilder createSocksClientBuilder(boolean isSocks4) {
        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(isSocks4
                        ? connectionPoolingManager.getSocks4ConnectionManager() : connectionPoolingManager.getSocksConnectionManager())
                .setConnectionManagerShared(true)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement();

        if (systemConfig.isUseSystemProperties()) {
            builder.useSystemProperties();
        }
        return builder;
    }


}
