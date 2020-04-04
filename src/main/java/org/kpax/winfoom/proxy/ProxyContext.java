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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ProxyContext implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(ProxyContext.class);

    @Autowired
    private LocalProxyServer localProxyServer;

    private ThreadPoolExecutor threadPool;

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

        logger.info("Done proxy context's initialization");
    }

    /**
     * Submit to the internal executor a {@link Runnable} instance for asynchronous execution.
     *
     * @param runnable The instance to be submitted for execution.
     * @return The <code>Future</code> instance.
     */
    public Future<?> executeAsync(Runnable runnable) {
        return threadPool.submit(runnable);
    }

    /**
     * Check whether the local proxy server is started.
     *
     * @return <code>true</code> iff the local proxy server is started.
     */
    public boolean isStarted() {
        return localProxyServer.isStarted();
    }

    @Override
    public void close() {
        logger.info("Close all context's resources");

        try {
            threadPool.shutdownNow();
        } catch (Exception e) {
            logger.warn("Error on closing thread pool", e);
        }
    }

}
