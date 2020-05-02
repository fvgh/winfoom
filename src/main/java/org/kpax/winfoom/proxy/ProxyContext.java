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

import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.Authenticator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * It provides a thread pool, a HTTP connection manager etc.<br/>
 * We rely on the Spring context to close this instance!
 *
 * @author Eugen Covaci
 */
@EnableScheduling
@Component
public class ProxyContext implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(ProxyContext.class);

    @Autowired
    private LocalProxyServer localProxyServer;

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ConnectionPoolingManager connectionPoolingManager;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    private ThreadPoolExecutor threadPool;

    @PostConstruct
    private void init() {
        logger.info("Create thread pool");

        this.threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new DefaultThreadFactory());

        logger.info("Done proxy context's initialization");
    }

    public synchronized boolean start() throws Exception {
        if (!isStarted()) {
            connectionPoolingManager.start();
            localProxyServer.start();
            return true;
        }
        return false;
    }

    public synchronized boolean stop() {
        if (isStarted()) {
            localProxyServer.close();
            connectionPoolingManager.stop();

            // Remove auth for SOCKS proxy
            if (proxyConfig.getProxyType().isSocks5()) {
                Authenticator.setDefault(null);
            }

            proxyBlacklist.clear();

            return true;
        }
        return false;
    }

    /**
     * Check whether the local proxy server is started.
     *
     * @return <code>true</code> iff the local proxy server is started.
     */
    public boolean isStarted() {
        return localProxyServer.isStarted();
    }

    public ExecutorService executorService() {
        return threadPool;
    }

    @Override
    public void close() {
        logger.info("Close all context's resources");
        stop();

        try {
            threadPool.shutdownNow();
        } catch (Exception e) {
            logger.warn("Error on closing thread pool", e);
        }

    }

    public int clearBlacklist() {
        return proxyBlacklist.clear();
    }

    public static class DefaultThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public DefaultThreadFactory() {
            SecurityManager securityManager = System.getSecurityManager();
            group = (securityManager != null) ? securityManager.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(group, runnable,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);

            // Make sure all threads are daemons!
            if (!thread.isDaemon()) {
                thread.setDaemon(true);
            }

            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        }
    }
}
