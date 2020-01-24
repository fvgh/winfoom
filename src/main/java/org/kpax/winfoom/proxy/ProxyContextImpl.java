/*******************************************************************************
 * Copyright (c) 2018 Eugen Covaci.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 *
 * Contributors:
 *     Eugen Covaci - initial design and implementation
 *******************************************************************************/

package org.kpax.winfoom.proxy;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.win.WindowsNTLMSchemeFactory;
import org.apache.http.impl.auth.win.WindowsNegotiateSchemeFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.kpax.winfoom.config.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * It provides a thread pool, a HTTP connection manager etc.
 * mostly for the {@link SocketHandler} instance.<br/>
 * We rely on the Spring context to close this instance!
 *
 * @author Eugen Covaci
 */
@Component
public class ProxyContextImpl implements ProxyContext {

    private final Logger logger = LoggerFactory.getLogger(ProxyContextImpl.class);

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Autowired
    private RequestConfig proxyRequestConfig;

    @Autowired
    private  SocketConfig socketConfig;

    private ThreadPoolExecutor threadPool;

    private PoolingHttpClientConnectionManager connectionManager;

    private Timer connectionEvictionTimer;

    @PostConstruct
    public void init() {
        logger.info("Create thread pool");

        // All threads are daemons!
        threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setDaemon(true);
                    return thread;
                });

        logger.info("Create pooling connection manager");
        connectionManager = new PoolingHttpClientConnectionManager();

        logger.info("Configure connection manager");
        if (systemConfig.getMaxConnections() != null) {
            connectionManager.setMaxTotal(systemConfig.getMaxConnections());
        }
        if (systemConfig.getMaxConnectionsPerRoute() != null) {
            connectionManager.setDefaultMaxPerRoute(systemConfig.getMaxConnectionsPerRoute());
        }

        logger.info("Create connection eviction timer");
        connectionEvictionTimer = new Timer();

        logger.info("Done proxy context's initialization");
    }

    @Override
    public void start() {
        if (systemConfig.isEvictionEnabled()) {
            logger.info("Start connection eviction task");
            connectionEvictionTimer.schedule(new EvictionTask(), 0, systemConfig.getEvictionPeriod() * 1000);
        }
        logger.info("Proxy context is ready");
    }

    @Override
    public CloseableHttpClient createHttpClientBuilder(boolean retries) {
        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
                .register(AuthSchemes.NTLM, new WindowsNTLMSchemeFactory(null))
                .register(AuthSchemes.SPNEGO, new WindowsNegotiateSchemeFactory(null))
                .build();
        HttpClientBuilder builder = WinHttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .setProxy(proxyRequestConfig.getProxy())
                .setDefaultRequestConfig(proxyRequestConfig)
                .setDefaultSocketConfig(socketConfig)
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setKeepAliveStrategy(new CustomConnectionKeepAliveStrategy())
                .disableRedirectHandling()
                .disableCookieManagement();
        if (!retries) {
            builder.disableAutomaticRetries();
        }
        if (systemConfig.isUseSystemProperties()) {
            builder.useSystemProperties();
        }
        return builder.build();
    }

    @Override
    public void executeAsync(Runnable runnable) {
        threadPool.execute(runnable);
    }

    @Override
    public void close() {
        logger.info("Close all context's resources");

        try {
            threadPool.shutdownNow();
        } catch (Exception e) {
            logger.warn("Error on closing thread pool", e);
        }

        try {
            connectionManager.close();
        } catch (Exception e) {
            logger.warn("Error on closing PoolingHttpClientConnectionManager instance", e);
        }

        if (connectionEvictionTimer != null) {
            try {
                connectionEvictionTimer.cancel();
            } catch (Exception e) {
                logger.warn("Error on closing connectionEvictionTimer", e);
            }
        }
    }


    private class CustomConnectionKeepAliveStrategy implements ConnectionKeepAliveStrategy {

        @Override
        public long getKeepAliveDuration(HttpResponse response, HttpContext context) {

            // Honor 'keep-alive' header
            HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement headerElement = it.nextElement();
                String value = headerElement.getValue();
                if (value != null && headerElement.getName().equalsIgnoreCase("timeout")) {
                    try {
                        return Long.parseLong(value) * 1000;
                    } catch (NumberFormatException ignore) {
                    }
                }
            }

            return systemConfig.getMaxConnectionIdle() * 1000;
        }

    }

    private class EvictionTask extends TimerTask {

        @Override
        public void run() {

            // Close expired connections
            connectionManager.closeExpiredConnections();

            // Close connections that have been idle
            // longer than MAX_CONNECTION_IDLE seconds
            connectionManager.closeIdleConnections(systemConfig.getMaxConnectionIdle(), TimeUnit.SECONDS);
        }
    }

}
