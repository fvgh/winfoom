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


import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HTTP;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.exception.PacFileException;
import org.kpax.winfoom.util.HeaderDateGenerator;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.InputOutputs;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Responsible for handling client's connection.
 */
@Component
class ClientConnectionHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private ProxyAutoConfig proxyAutoconfig;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    @Autowired
    private ClientProcessorSelector clientProcessorSelector;

    /**
     * Process the client connection with each available proxy.<br>
     * Un un-responding to connect proxy is blacklisted only if it is not the last
     * one available.<br>
     * <b>Note:</b> If the {@link ClientConnection} is successfully created,
     * a proper response <i>must</i> be sent to the client.
     *
     * @param socket the client's socket
     * @throws IOException
     * @throws HttpException
     */
    void handleConnection(final Socket socket) throws IOException, HttpException {

        final ClientConnection clientConnection;
        try {
            clientConnection = new ClientConnection(socket);
        } catch (HttpException e) {
            // Most likely a bad request
            // even though might not always be the case
            // Still, we give something back to the client
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(
                    ObjectFormat.toCrlf(HttpUtils.toStatusLine(HttpStatus.SC_BAD_REQUEST, e.getMessage())));
            outputStream.write(
                    ObjectFormat.toCrlf(HttpUtils.createHttpHeader(HTTP.DATE_HEADER, new HeaderDateGenerator().getCurrentDate())));
            outputStream.write(ObjectFormat.CRLF.getBytes());
            throw e;
        }

        RequestLine requestLine = clientConnection.getRequestLine();
        logger.debug("Handle request: {}", requestLine);

        try {
            List<ProxyInfo> proxyInfoList;
            if (proxyConfig.isAutoConfig()) {
                URI requestUri = clientConnection.getRequestUri();
                logger.debug("Extracted URI from request {}", requestUri);
                proxyInfoList = proxyAutoconfig.findProxyForURL(requestUri);
            } else {

                // Manual proxy case
                HttpHost proxyHost = proxyConfig.getProxyType().isDirect() ? null :
                        new HttpHost(proxyConfig.getProxyHost(), proxyConfig.getProxyPort());
                logger.debug("Manual case, proxy host: {}", proxyHost);
                proxyInfoList = Collections.singletonList(new ProxyInfo(proxyConfig.getProxyType(), proxyHost));
            }
            logger.debug("proxyInfoList {}", proxyInfoList);

            ClientConnectionProcessor connectionProcessor;
            for (Iterator<ProxyInfo> itr = proxyInfoList.iterator(); itr.hasNext(); ) {
                ProxyInfo proxyInfo = itr.next();
                if (itr.hasNext()) {
                    if (proxyBlacklist.checkBlacklist(proxyInfo)) {
                        logger.debug("Blacklisted proxy {} - skip it", proxyInfo);
                        continue;
                    }
                }
                connectionProcessor = clientProcessorSelector.selectClientProcessor(requestLine, proxyInfo);
                try {
                    logger.debug("Process connection with proxy: {}", proxyInfo);
                    connectionProcessor.process(clientConnection, proxyInfo);

                    // Success, break the iteration
                    break;
                } catch (Exception e) {
                    if (e instanceof ConnectException || e.getCause() instanceof ConnectException) {
                        logger.debug("Connection error", e);
                        if (itr.hasNext()) {
                            logger.debug("Failed to process connection with proxy: {}, retry with the next one",
                                    proxyInfo);
                            proxyBlacklist.blacklist(proxyInfo);
                        } else {
                            logger.debug("Failed to process connection with proxy: {}, send the error response",
                                    proxyInfo);

                            // Cannot connect to the remote proxy
                            clientConnection.writeErrorResponse(HttpStatus.SC_BAD_GATEWAY, e);
                        }
                    } else {
                        if (HttpUtils.isConnectionAborted(e)) {
                            logger.debug("Client's connection aborted", e);
                        } else {
                            logger.debug("Generic error, send the error response", e);

                            // Any other error, including client errors
                            clientConnection.writeErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
                        }
                        break;
                    }
                }
            }
        } catch (PacFileException e) {
            clientConnection.writeErrorResponse(requestLine.getProtocolVersion(),
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Proxy Auto Config file error");
            logger.debug("Proxy Auto Config file error", e);
        } catch (Exception e) {
            clientConnection.writeErrorResponse(requestLine.getProtocolVersion(),
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());
            logger.debug("Error on handling request", e);
        } finally {
            InputOutputs.close(clientConnection);
        }
        logger.debug("Done handling request: {}", requestLine);

    }
}
