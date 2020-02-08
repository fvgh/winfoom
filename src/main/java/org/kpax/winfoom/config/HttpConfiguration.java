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

package org.kpax.winfoom.config;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 11/20/2019
 */
@Configuration
public class HttpConfiguration {

    private final Logger logger = LoggerFactory.getLogger(HttpConfiguration.class);

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Bean
    public RequestConfig getProxyRequestConfig() {
        logger.debug("Create proxy request config");
        HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
        return RequestConfig.custom()
                .setProxy(proxy)
                .setCircularRedirectsAllowed(true)
                .build();
    }

    @Bean
    public SocketConfig getSocketConfig() {
        logger.debug("Create socket config");
        return SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSndBufSize(systemConfig.getSocketBufferSize())
                .setRcvBufSize(systemConfig.getSocketBufferSize())
                .build();
    }
}
