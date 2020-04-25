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

package org.kpax.winfoom.proxy.conn;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.util.LocalIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConnectionSocketFactoryManager implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    private volatile PoolingHttpClientConnectionManager httpConnectionManager;

    private volatile PoolingHttpClientConnectionManager socksConnectionManager;

    private volatile PoolingHttpClientConnectionManager socks4ConnectionManager;

    public PoolingHttpClientConnectionManager getHttpConnectionManager() {
        if (httpConnectionManager == null) {
            synchronized (this) {
                if (httpConnectionManager == null) {
                    httpConnectionManager = createConnectionManager(null);
                }
            }
        }
        return httpConnectionManager;

    }

    public PoolingHttpClientConnectionManager getSocksConnectionManager() {
        if (socksConnectionManager == null) {
            synchronized (this) {
                if (socksConnectionManager == null) {
                    socksConnectionManager = createSocksConnectionManager(false);
                }
            }
        }
        return socksConnectionManager;
    }

    public PoolingHttpClientConnectionManager getSocks4ConnectionManager() {
        if (socks4ConnectionManager == null) {
            synchronized (this) {
                if (socks4ConnectionManager == null) {
                    socks4ConnectionManager = createSocksConnectionManager(true);
                }
            }
        }
        return socks4ConnectionManager;
    }

    public List<PoolingHttpClientConnectionManager> getAllActiveConnectionManagers() {
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

    private PoolingHttpClientConnectionManager createConnectionManager(Registry<ConnectionSocketFactory> socketFactoryRegistry) {
        PoolingHttpClientConnectionManager connectionManager = socketFactoryRegistry != null
                ? new PoolingHttpClientConnectionManager(socketFactoryRegistry) : new PoolingHttpClientConnectionManager();
        applySystemSettings(connectionManager);
        return connectionManager;
    }

    private PoolingHttpClientConnectionManager createSocksConnectionManager(boolean isSocks4) {
        SocksConnectionSocketFactory connectionSocketFactory = isSocks4
                ? new Socks4ConnectionSocketFactory() : new SocksConnectionSocketFactory();
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", connectionSocketFactory)
                .register("https", connectionSocketFactory)
                .build();

        return createConnectionManager(socketFactoryRegistry);
    }

    private void applySystemSettings(final PoolingHttpClientConnectionManager connectionManager) {
        logger.info("Configure connection manager");
        if (systemConfig.getMaxConnections() != null) {
            connectionManager.setMaxTotal(systemConfig.getMaxConnections());
        }
        if (systemConfig.getMaxConnectionsPerRoute() != null) {
            connectionManager.setDefaultMaxPerRoute(systemConfig.getMaxConnectionsPerRoute());
        }
    }

    @Override
    public void close() {
        LocalIOUtils.close(httpConnectionManager);
        LocalIOUtils.close(socksConnectionManager);
        LocalIOUtils.close(socks4ConnectionManager);
        httpConnectionManager = null;
        socksConnectionManager = null;
        socks4ConnectionManager = null;
    }
}
