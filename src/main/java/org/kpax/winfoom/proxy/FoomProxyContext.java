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

import org.apache.http.*;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.win.WindowsNTLMSchemeFactory;
import org.apache.http.impl.auth.win.WindowsNegotiateSchemeFactory;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;

/**
 * It provides a thread pool, a HTTP connection manager etc.
 * mostly for the {@link SocketHandler} instance.<br/>
 * We rely on the Spring context to close this instance!
 *
 * @author Eugen Covaci
 */
@Component
class FoomProxyContext implements ProxyContext {

    private final Logger logger = LoggerFactory.getLogger(FoomProxyContext.class);

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Autowired
    private SocketConfig socketConfig;

    private ThreadPoolExecutor threadPool;

    private PoolingHttpClientConnectionManager connectionManager;

    private FoomDefaultHttpRequestRetryHandler retryHandler;

    private ProxyAuthenticationRequiredRetryStrategy retryStrategy;

    @PostConstruct
    private void init() {
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

        logger.info("Configure retry strategy");
        retryHandler = new FoomDefaultHttpRequestRetryHandler();
        retryStrategy = new ProxyAuthenticationRequiredRetryStrategy(systemConfig.getRepeatsOnFailure());

        logger.info("Done proxy context's initialization");
    }

    @Override
    public HttpClientBuilder createHttpClientBuilder() {
        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
                .register(AuthSchemes.NTLM, new WindowsNTLMSchemeFactory(null))
                .register(AuthSchemes.SPNEGO, new WindowsNegotiateSchemeFactory(null))
                .build();
        RequestConfig requestConfig = createRequestConfig();
        HttpClientBuilder builder = WinHttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy())
                .setProxy(requestConfig.getProxy())
                .setDefaultRequestConfig(requestConfig)
                .setDefaultSocketConfig(socketConfig)
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
                .setServiceUnavailableRetryStrategy(retryStrategy)
                .setRetryHandler(retryHandler)
                .disableRedirectHandling()
                .disableCookieManagement();

        if (systemConfig.isUseSystemProperties()) {
            builder.useSystemProperties();
        }
        return builder;
    }

    @Override
    public Future<?> executeAsync(Runnable runnable) {
        return threadPool.submit(runnable);
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
    }

    private RequestConfig createRequestConfig() {
        logger.debug("Create proxy request config");
        HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
        return RequestConfig.custom()
                .setProxy(proxy)
                .setCircularRedirectsAllowed(true)
                .build();
    }

    private static class ProxyAuthenticationRequiredRetryStrategy implements ServiceUnavailableRetryStrategy {

        private int maxExecutionCount;

        public ProxyAuthenticationRequiredRetryStrategy(int maxExecutionCount) {
            this.maxExecutionCount = maxExecutionCount;
        }

        @Override
        public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpRequest request = HttpClientContext.adapt(context).getRequest();

            /*
            Repeat the request on 407 Proxy Authentication Required error code
            but only if the request has no body or a repeatable one.
             */
            return statusCode == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED
                    && executionCount < this.maxExecutionCount
                    && (!(request instanceof HttpEntityEnclosingRequest)
                           || ((HttpEntityEnclosingRequest) request).getEntity().isRepeatable());
        }

        @Override
        public long getRetryInterval() {
            return 0;
        }
    }

    private static class FoomDefaultHttpRequestRetryHandler extends DefaultHttpRequestRetryHandler {

        @Override
        protected boolean handleAsIdempotent(HttpRequest request) {

            /*
            Allow repeating also when
            the request has a repeatable body
             */
            return !(request instanceof HttpEntityEnclosingRequest)
                    || ((HttpEntityEnclosingRequest) request).getEntity().isRepeatable();
        }

    }

}
