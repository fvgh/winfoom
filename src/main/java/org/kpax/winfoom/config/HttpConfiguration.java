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

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.kpax.winfoom.event.AfterServerStopEvent;
import org.kpax.winfoom.proxy.ProxyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com} Created on
 * 11/20/2019
 */
@EnableScheduling
@Configuration
class HttpConfiguration {

    private final Logger logger = LoggerFactory.getLogger(HttpConfiguration.class);

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyContext proxyContext;

    /**
     * @return The HTTP connection manager.
     */
    @Bean
    PoolingHttpClientConnectionManager connectionManager() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

        logger.info("Configure connection manager");
        if (systemConfig.getMaxConnections() != null) {
            connectionManager.setMaxTotal(systemConfig.getMaxConnections());
        }
        if (systemConfig.getMaxConnectionsPerRoute() != null) {
            connectionManager.setDefaultMaxPerRoute(systemConfig.getMaxConnectionsPerRoute());
        }

        return connectionManager;
    }

    /**
     * Purges the pooled HTTP connection manager after stopping the local proxy.
     *
     * @return The <code>ApplicationListener<AfterServerStopEvent></code> instance.
     */
    @Bean
    ApplicationListener<AfterServerStopEvent> onServerStopEventApplicationListener() {
        return event -> {
            logger.info("Close expired/idle connections");
            PoolingHttpClientConnectionManager connectionManager = connectionManager();
            connectionManager.closeExpiredConnections();
            connectionManager.closeIdleConnections(0, TimeUnit.SECONDS);
        };
    }

    /**
     * A job that closes the idle/expired HTTP connections.
     */
    @Scheduled(fixedRateString = "#{systemConfig.connectionManagerCleanInterval * 1000}")
    void cleanUpConnectionManager() {
        if (proxyContext.isStarted()) {
            logger.debug("Execute connection manager pool clean up task");
            PoolingHttpClientConnectionManager connectionManager = connectionManager();
            connectionManager.closeExpiredConnections();
            connectionManager.closeIdleConnections(systemConfig.getConnectionManagerIdleTimeout(), TimeUnit.SECONDS);
            if (logger.isDebugEnabled()) {
                logger.debug("PoolingHttpClientConnectionManager statistics {}", connectionManager.getTotalStats());
            }
        }
    }

}
