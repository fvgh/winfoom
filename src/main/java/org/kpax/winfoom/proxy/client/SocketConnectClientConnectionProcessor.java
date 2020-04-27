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

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HTTP;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.proxy.ProxyContext;
import org.kpax.winfoom.proxy.ProxyInfo;
import org.kpax.winfoom.util.HeaderDateGenerator;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/16/2020
 */
@Component
public class SocketConnectClientConnectionProcessor implements ClientConnectionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyContext proxyContext;

    @Override
    public void process(ClientConnection clientConnection, ProxyInfo proxyInfo)
            throws IOException {

        RequestLine requestLine = clientConnection.getHttpRequest().getRequestLine();
        HttpHost target = HttpHost.create(requestLine.getUri());

        Proxy proxy = null;

        if (proxyInfo.getType().isSocks()) {
            proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(userConfig.getProxyHost(), userConfig.getProxyPort()));
        }

        try (Socket socket = proxy != null ? new Socket(proxy) : new Socket()) {
            socket.setSoTimeout(systemConfig.getSocketChannelTimeout() * 1000);
            if (userConfig.getProxyType().isSocks4()) {
                try {
                    HttpUtils.setSocks4(socket);
                } catch (UnsupportedOperationException e) {// FIXME Out of here
                    logger.debug("Error on setting SOCKS 4 version", e);
                    clientConnection.writeErrorResponse(clientConnection.getHttpRequest().getProtocolVersion(),
                            HttpStatus.SC_INTERNAL_SERVER_ERROR, "SOCKS 4 not supported by the JVM");
                    return;
                }
            }
            logger.debug("Open connection");
            socket.connect(new InetSocketAddress(target.getHostName(), target.getPort()),
                    systemConfig.getSocketChannelTimeout() * 1000);
            logger.debug("Connected to {}", target);

            // Respond with 200 code
            clientConnection.write(String.format("%s 200 Connection established",
                    requestLine.getProtocolVersion()));
            clientConnection.write(HttpUtils.createHttpHeader(HTTP.DATE_HEADER,
                    new HeaderDateGenerator().getCurrentDate()));
            clientConnection.writeln();

            try {
                // The proxy facade mediates the full duplex communication
                // between the client and the remote proxy
                IoUtils.duplex(proxyContext.executorService(),
                        socket.getInputStream(),
                        socket.getOutputStream(),
                        clientConnection.getInputStream(),
                        clientConnection.getOutputStream());
            } catch (Exception e) {
                logger.error("Error on full duplex", e);
            }

        }
    }

}
