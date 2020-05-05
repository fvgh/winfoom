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

import org.apache.http.RequestLine;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Select the appropriate {@link ClientConnectionProcessor} implementation.
 */
@Component
class ClientProcessorSelector {

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private HttpConnectClientConnectionProcessor httpConnectClientConnectionProcessor;

    @Autowired
    private SocketConnectClientConnectionProcessor socketConnectClientConnectionProcessor;

    @Autowired
    private NonConnectClientConnectionProcessor nonConnectClientConnectionProcessor;

    /**
     * Select the appropriate {@link ClientConnectionProcessor} implementation to process the client's connection
     * based on the request info and the proxy type.
     *
     * @param requestLine the HTTP request's first line.
     * @param proxyInfo   the proxy info used to make the remote HTTP request.
     * @return the processor instance.
     */
    public ClientConnectionProcessor selectClientProcessor(RequestLine requestLine, ProxyInfo proxyInfo) {
        if (HttpUtils.HTTP_CONNECT.equalsIgnoreCase(requestLine.getMethod())) {
            if (proxyInfo.getType().isSocks() || proxyInfo.getType().isDirect()) {
                return socketConnectClientConnectionProcessor;
            }
            return httpConnectClientConnectionProcessor;
        } else {
            return nonConnectClientConnectionProcessor;
        }
    }
}
