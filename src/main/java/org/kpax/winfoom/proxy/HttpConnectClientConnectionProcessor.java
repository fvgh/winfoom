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

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.RequestLine;
import org.apache.http.impl.execchain.TunnelRefusedException;
import org.kpax.winfoom.util.InputOutputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Process a CONNECT request through a HTTP proxy.
 *
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/13/2020
 */
@Component
class HttpConnectClientConnectionProcessor implements ClientConnectionProcessor {

    private final Logger logger = LoggerFactory.getLogger(HttpConnectClientConnectionProcessor.class);

    @Autowired
    private ProxyContext proxyContext;

    @Autowired
    private TunnelConnection tunnelConnection;

    @Override
    public void process(final ClientConnection clientConnection, final ProxyInfo proxyInfo)
            throws IOException, HttpException {
        logger.debug("Handle HTTP connect request");
        RequestLine requestLine = clientConnection.getRequestLine();
        HttpHost target = HttpHost.create(requestLine.getUri());
        HttpHost proxy = new HttpHost(proxyInfo.getProxyHost().getHostName(), proxyInfo.getProxyHost().getPort());

        try (Tunnel tunnel = tunnelConnection.open(proxy, target, requestLine.getProtocolVersion())) {
            try {
                // Handle the tunnel response
                logger.debug("Write status line");
                clientConnection.write(tunnel.getStatusLine());

                logger.debug("Write headers");
                for (Header header : tunnel.getResponse().getAllHeaders()) {
                    clientConnection.write(header);
                }
                clientConnection.writeln();

                // The proxy facade mediates the full duplex communication
                // between the client and the remote proxy.
                // This usually ends on connection reset, timeout or any other error
                InputOutputs.duplex(proxyContext.executorService(),
                        tunnel.getInputStream(),
                        tunnel.getOutputStream(),
                        clientConnection.getInputStream(),
                        clientConnection.getOutputStream());

            } catch (Exception e) {
                logger.debug("Error on handling CONNECT response", e);
            }
        } catch (TunnelRefusedException tre) {
            logger.debug("The tunnel request was rejected by the proxy host", tre);
            try {
                clientConnection.writeHttpResponse(tre.getResponse());
            } catch (Exception e) {
                logger.debug("Error on writing response", e);
            }
        }

    }

}
