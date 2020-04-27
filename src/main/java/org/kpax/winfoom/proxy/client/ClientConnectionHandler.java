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

package org.kpax.winfoom.proxy.client;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.RequestLine;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.exception.InvalidPacFileException;
import org.kpax.winfoom.proxy.PacFile;
import org.kpax.winfoom.proxy.ProxyInfo;
import org.kpax.winfoom.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ClientConnectionHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Socket socket;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private ClientProcessorSelector clientProcessorSelector;

    @Autowired
    private PacFile pacFile;

    public ClientConnectionHandler bind(final Socket socket) {
        Assert.isNull(this.socket, "Socket already bound!");
        this.socket = socket;
        return this;
    }

    public void handleConnection() throws IOException, HttpException, URISyntaxException, InvalidPacFileException {
        try {
            ClientConnection connection = ClientConnection.create(socket);
            RequestLine requestLine = connection.getHttpRequest().getRequestLine();
            logger.debug("Handle request: {}", requestLine);

            List<ProxyInfo> proxyInfos;
            if (userConfig.getProxyType().isPac()) {
                HttpHost host = HttpHost.create(requestLine.getUri());
                proxyInfos = pacFile.loadListProxyInfos(host);
            } else {// Manual proxy case
                HttpHost proxyHost = null;
                if (!userConfig.getProxyType().isDirect()) {
                    proxyHost = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
                }
                proxyInfos = Collections.singletonList(new ProxyInfo(userConfig.getProxyType().toProxyInfoType(), proxyHost));
            }
            logger.debug("proxyInfos {}", proxyInfos);

            ClientConnectionProcessor connectionProcessor;
            for (Iterator<ProxyInfo> itr = proxyInfos.iterator(); itr.hasNext(); ) {
                ProxyInfo proxyInfo = itr.next();
                if (!itr.hasNext()) {
                    connection.lastResort();
                }
                connectionProcessor = clientProcessorSelector.selectClientProcessor(requestLine, proxyInfo);
                try {
                    logger.debug("Process connection with proxyInfo: {}", proxyInfo);
                    connectionProcessor.process(connection, proxyInfo);
                } catch (ConnectException e) {
                    if (itr.hasNext()) {
                        continue;
                    } else {
                        // Cannot connect to the remote proxy
                        connection.writeErrorResponse(HttpStatus.SC_BAD_GATEWAY, e);
                    }
                } catch (Exception e) {
                    // Any other error, including client errors
                    connection.writeErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
                    break;
                }
            }
        } finally {
            IoUtils.close(socket);
        }

    }


}
