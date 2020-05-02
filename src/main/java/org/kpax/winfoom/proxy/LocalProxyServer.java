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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.ConnectionClosedException;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * The local proxy server.
 * We rely on the Spring context to close this instance!
 *
 * @author Eugen Covaci
 */
@Component
class LocalProxyServer implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(LocalProxyServer.class);

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private ProxyContext proxyContext;

    @Autowired
    private ApplicationContext applicationContext;

    private ServerSocket serverSocket;

    private volatile boolean started;

    /**
     * Starts the local proxy server.
     * If the server had been started, an {@link IllegalStateException} would be thrown.
     *
     * @throws Exception
     */
    synchronized void start()
            throws Exception {
        if (started) {
            throw new IllegalStateException("Server already started!");
        }
        logger.info("Start local proxy server with userConfig {}", proxyConfig);

        try {
            serverSocket = new ServerSocket(proxyConfig.getLocalPort(),
                    systemConfig.getServerSocketBacklog());

            started = true;

            proxyContext.executorService().execute(() -> {
                while (started) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(systemConfig.getSocketSoTimeout() * 1000);
                        proxyContext.executorService().execute(() -> {
                            try {
                                applicationContext.getBean(ClientConnection.class, socket)
                                        .handleRequest();
                            } catch (BeanCreationException e) {
                                if (e.getCause() instanceof BeanInstantiationException) {
                                    Throwable cause = e.getCause().getCause();
                                    if (cause instanceof ConnectionClosedException) {
                                        logger.debug("Client unexpectedly closed connection", cause);
                                    } else {
                                        logger.debug("Error on init connection", cause);
                                    }
                                } else {
                                    logger.error("Error on instantiating ClientConnection", e);
                                }
                            } catch (Exception e) {
                                logger.error("Error on handling connection", e);
                            }
                        });
                    } catch (SocketException e) {

                        // Ignore java.net.SocketException: Interrupted function call.
                        // Get this whenever stop the server socket.
                        if (!StringUtils.startsWithIgnoreCase(e.getMessage(), "Interrupted function call")) {
                            logger.debug("Socket error on getting connection", e);
                        }
                    } catch (Exception e) {
                        logger.debug("Generic error on getting connection", e);
                    }
                }
            });

            try {
                // Save the user properties
                proxyConfig.save();
            } catch (Exception e) {
                logger.warn("Error on saving user configuration", e);
            }

            logger.info("Server started, listening on port: " + proxyConfig.getLocalPort());
        } catch (Exception e) {
            // Cleanup on exception
            close();
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        if (this.started) {
            this.started = false;
            logger.info("Now stop running the local proxy server");
            try {
                logger.info("Close the server socket");
                serverSocket.close();
            } catch (Exception e) {
                logger.warn("Error on closing server socket", e);
            }
        } else {
            logger.info("Already closed, nothing to do");
        }
    }

    synchronized boolean isStarted() {
        return started;
    }

}
