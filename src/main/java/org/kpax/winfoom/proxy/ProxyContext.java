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

import org.apache.http.impl.client.CloseableHttpClient;

import java.io.Closeable;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 1/22/2020
 */
interface ProxyContext extends Closeable {
    /**
     * After this method call, the proxy should be ready
     * for handling HTTP(s) requests.
     */
    void start();

    CloseableHttpClient createHttpClientBuilder(boolean retry);

    void executeAsync(Runnable runnable);

}
