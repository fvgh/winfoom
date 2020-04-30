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
import org.apache.http.HttpHost;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HTTP;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.util.HeaderDateGenerator;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.InputOutputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;

/**
 * Process a CONNECT request through a SOCKS proxy or no proxy.
 *
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/16/2020
 */
@Component
class SocketConnectClientConnectionProcessor implements ClientConnectionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyContext proxyContext;

    @Override
    public void process(ClientConnection clientConnection, ProxyInfo proxyInfo)
            throws IOException {
        logger.debug("Handle socket connect request");
        RequestLine requestLine = clientConnection.getHttpRequest().getRequestLine();
        HttpHost target = HttpHost.create(requestLine.getUri());

        Proxy proxy;
        if (proxyInfo.getType().isSocks()) {
            proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(proxyInfo.getHost().getHostName(), proxyInfo.getHost().getPort()));
        } else {
            proxy = Proxy.NO_PROXY;
        }

        try (Socket socket = new Socket(proxy)) {
            socket.setSoTimeout(systemConfig.getSocketSoTimeout() * 1000);
            if (proxyInfo.getType().isSocks4()) {
                HttpUtils.setSocks4(socket);
            }
            logger.debug("Open connection");
            try {
                socket.connect(new InetSocketAddress(target.getHostName(), target.getPort()),
                        systemConfig.getSocketConnectTimeout() * 1000);
            } catch (SocketException e) {
                if (StringUtils.startsWithIgnoreCase(e.getMessage(), "Connection refused")) {
                    throw new ConnectException(e.getMessage());
                }
                ;
            }
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
                InputOutputs.duplex(proxyContext.executorService(),
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
