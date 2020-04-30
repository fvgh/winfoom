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

/**
 * Select the appropriate {@link ClientConnectionProcessor} implementation.
 */
public interface ClientProcessorSelector {

    /**
     * Select the appropriate {@link ClientConnectionProcessor} implementation to process the client's connection based on the request info and the proxy type.
     *
     * @param requestLine the HTTP request's first line.
     * @param proxyInfo   the proxy info used to make the remote HTTP request.
     * @return the processor instance.
     */
    ClientConnectionProcessor selectClientProcessor(RequestLine requestLine, ProxyInfo proxyInfo);

}
