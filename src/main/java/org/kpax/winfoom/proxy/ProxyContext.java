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

    CloseableHttpClient createHttpClientBuilder(boolean retries);

    void executeAsync(Runnable runnable);

}
