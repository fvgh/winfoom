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

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.util.InputOutputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * It manages the HTTP connection pooling mechanism.<br>
 * Only used for non-CONNECT HTTP requests.
 */
@Component
class ConnectionPoolingManager implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private SystemConfig systemConfig;

    /**
     * For HTTP proxy type
     */
    private volatile PoolingHttpClientConnectionManager httpConnectionManager;

    /**
     * For SOCKS5 proxy type
     */
    private volatile PoolingHttpClientConnectionManager socksConnectionManager;

    /**
     * For SOCKS4 proxy type
     */
    private volatile PoolingHttpClientConnectionManager socks4ConnectionManager;

    /**
     * Whether this manager is started or not.
     */
    private volatile boolean started;

    /**
     * Lazy getter for HTTP proxy.
     *
     * @return the existent {@link PoolingHttpClientConnectionManager} instance or a new one if {@code null}.
     */
    PoolingHttpClientConnectionManager getHttpConnectionManager() {
        if (httpConnectionManager == null) {
            synchronized (this) {
                if (httpConnectionManager == null) {
                    httpConnectionManager = createConnectionManager(null);
                }
            }
        }
        return httpConnectionManager;

    }

    /**
     * Lazy getter for SOCKS5 proxy.
     *
     * @return the existent {@link PoolingHttpClientConnectionManager} instance or a new one if {@code null}.
     */
    PoolingHttpClientConnectionManager getSocksConnectionManager() {
        if (socksConnectionManager == null) {
            synchronized (this) {
                if (socksConnectionManager == null) {
                    socksConnectionManager = createSocksConnectionManager(false);
                }
            }
        }
        return socksConnectionManager;
    }

    /**
     * Lazy getter for SOCKS4 proxy.
     *
     * @return the existent {@link PoolingHttpClientConnectionManager} instance or a new one if {@code null}.
     */
    PoolingHttpClientConnectionManager getSocks4ConnectionManager() {
        if (socks4ConnectionManager == null) {
            synchronized (this) {
                if (socks4ConnectionManager == null) {
                    socks4ConnectionManager = createSocksConnectionManager(true);
                }
            }
        }
        return socks4ConnectionManager;
    }

    /**
     * If not started it marks this manager as started, does nothing otherwise.
     */
    synchronized void start() {
        if (!started) {
            started = true;
        }
    }

    /**
     * @return all active {@link PoolingHttpClientConnectionManager} instances
     */
    private List<PoolingHttpClientConnectionManager> getAllActiveConnectionManagers() {
        List<PoolingHttpClientConnectionManager> activeConnectionManagers = new ArrayList<>();
        if (httpConnectionManager != null) {
            activeConnectionManagers.add(httpConnectionManager);
        }
        if (socksConnectionManager != null) {
            activeConnectionManagers.add(socksConnectionManager);
        }
        if (socks4ConnectionManager != null) {
            activeConnectionManagers.add(socks4ConnectionManager);
        }
        return activeConnectionManagers;
    }

    boolean isStarted() {
        return started;
    }

    /**
     * A job that closes the idle/expired HTTP connections.
     */
    @Scheduled(fixedRateString = "#{systemConfig.connectionManagerCleanInterval * 1000}")
    void cleanUpConnectionManager() {
        if (isStarted()) {
            logger.debug("Execute connection manager pool clean up task");
            for (PoolingHttpClientConnectionManager connectionManager : getAllActiveConnectionManagers()) {
                try {
                    connectionManager.closeExpiredConnections();
                    connectionManager.closeIdleConnections(systemConfig.getConnectionManagerIdleTimeout(), TimeUnit.SECONDS);
                    if (logger.isDebugEnabled()) {
                        logger.debug("PoolingHttpClientConnectionManager statistics {}", connectionManager.getTotalStats());
                    }
                } catch (Exception e) {
                    logger.debug("Error on cleaning connection pool", e);
                }
            }
        }
    }

    /**
     * It creates a generic {@link PoolingHttpClientConnectionManager}
     *
     * @param socketFactoryRegistry the {@link Registry} instance used to configure the {@link PoolingHttpClientConnectionManager}.
     * @return the new {@link PoolingHttpClientConnectionManager} instance.
     * @throws IllegalStateException when this manager is not started.
     */
    private PoolingHttpClientConnectionManager createConnectionManager(Registry<ConnectionSocketFactory> socketFactoryRegistry) {
        if (!started) {
            throw new IllegalStateException("Cannot create connectionManagers: ConnectionPoolingManager is not started");
        }
        PoolingHttpClientConnectionManager connectionManager = socketFactoryRegistry != null
                ? new PoolingHttpClientConnectionManager(socketFactoryRegistry) : new PoolingHttpClientConnectionManager();
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
     * It creates a SOCKS {@link PoolingHttpClientConnectionManager}
     *
     * @param isSocks4 whether the SOCKS version is {@code 4} or not.
     * @return the new {@link PoolingHttpClientConnectionManager} instance.
     * @throws IllegalStateException when this manager is not started.
     */
    private PoolingHttpClientConnectionManager createSocksConnectionManager(boolean isSocks4) {
        ConnectionSocketFactory connectionSocketFactory = isSocks4
                ? new Socks4ConnectionSocketFactory() : new SocksConnectionSocketFactory();
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", connectionSocketFactory)
                .register("https", connectionSocketFactory)
                .build();

        return createConnectionManager(socketFactoryRegistry);
    }

    /**
     * If started closes all active {@link PoolingHttpClientConnectionManager} instances then nullifies them,
     * otherwise does nothing.
     *
     * @return {@code true} iff this manager is started.
     */
    synchronized boolean stop() {
        if (started) {
            started = false;
            InputOutputs.close(httpConnectionManager);
            InputOutputs.close(socksConnectionManager);
            InputOutputs.close(socks4ConnectionManager);
            httpConnectionManager = null;
            socksConnectionManager = null;
            socks4ConnectionManager = null;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void close() {
        stop();
    }
}
