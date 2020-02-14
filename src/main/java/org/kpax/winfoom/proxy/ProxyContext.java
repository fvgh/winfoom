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
import java.util.concurrent.Future;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 1/22/2020
 */
interface ProxyContext extends Closeable {

    /**
     * Configures and create a {@link org.apache.http.impl.client.CloseableHttpClient} .
     * @param retry whether retries will be performed on failure.
     * @return an instance of {@link org.apache.http.impl.client.CloseableHttpClient}.
     */
    CloseableHttpClient createHttpClient(boolean retry);

    /**
     * Submit to the internal executor a {@link Runnable} instance for asynchronous execution.
     * @param runnable The instance to be submitted for execution.
     * @return The <code>Future</code> instance.
     */
    Future<?> executeAsync(Runnable runnable);

}
