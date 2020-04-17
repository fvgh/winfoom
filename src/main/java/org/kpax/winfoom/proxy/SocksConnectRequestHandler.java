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

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpDateGenerator;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
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
public class SocksConnectRequestHandler implements RequestHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final HttpDateGenerator httpDateGenerator = new HttpDateGenerator();

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyContext proxyContext;

    @Override
    public void handleRequest(HttpRequest request,
                              SessionInputBufferImpl sessionInputBuffer,
                              AsynchronousSocketChannelWrapper socketChannelWrapper)
            throws IOException {

        RequestLine requestLine = request.getRequestLine();
        Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                new InetSocketAddress(userConfig.getProxyHost(), userConfig.getProxyPort()));
        HttpHost target = HttpHost.create(requestLine.getUri());

        try (Socket socket = new Socket(proxy)) {

            logger.debug("Open connection");
            socket.connect(new InetSocketAddress(target.getHostName(), target.getPort()), systemConfig.getSocketChannelTimeout() * 1000);
            logger.debug("Connected to {}", target);

            // Respond with 200 code
            socketChannelWrapper.write(String.format("%s 200 Connection established", requestLine.getProtocolVersion()));
            socketChannelWrapper.write(HttpUtils.createHttpHeader(HTTP.DATE_HEADER, httpDateGenerator.getCurrentDate()));
            socketChannelWrapper.writeln();

            try {
                // The proxy facade mediates the full duplex communication
                // between the client and the remote proxy
                LocalIOUtils.duplex(proxyContext.executorService(),
                        socket.getInputStream(),
                        socket.getOutputStream(),
                        socketChannelWrapper.getInputStream(),
                        socketChannelWrapper.getOutputStream());
            } catch (Exception e) {
                logger.error("Error on full duplex", e);
            }

        }
    }

}
