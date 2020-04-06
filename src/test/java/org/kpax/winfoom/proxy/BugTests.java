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


import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BugTests {

    int SERVER_SOCKET_PORT = 3128;

    private AsynchronousServerSocketChannel serverSocket;

    @BeforeAll
    void before() throws IOException {


        serverSocket = AsynchronousServerSocketChannel.open()
                .bind(new InetSocketAddress(SERVER_SOCKET_PORT));
        serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

            public void completed(AsynchronousSocketChannel socketChanel, Void att) {
                try {
                    // accept the next connection
                    serverSocket.accept(null, this);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Handle this connection.
                try {

                    // Prepare request parsing (this is the client's request)
                    SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(4 * 1024);

                    InputStream inputStream = new InputStream() {

                        @Override
                        public int read() {
                            throw new RuntimeException("Do not use it");
                        }

                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
                            try {
                                return socketChanel.read(buffer).get();
                            } catch (ExecutionException e) {
                                throw new IOException(e.getCause());
                            } catch (Exception e) {
                                throw new IOException(e);
                            }

                        }

                    };

                    // Parse the request (all but the message body)
                    ClassicHttpRequest request = new DefaultHttpRequestParser().parse(inputBuffer, inputStream);
                    System.out.println("*** Request URI [" + request.getRequestUri() + "]");

                    // We do not give a response back to the client
                    // so this connection will hang !!!
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            public void failed(Throwable exc, Void att) {
                if (!(exc instanceof java.nio.channels.AsynchronousCloseException)) {
                    exc.printStackTrace();
                }
            }

        });
    }



    @Test
    void request_Connect() throws IOException, URISyntaxException {
        HttpHost localProxy = new HttpHost("http", "localhost", SERVER_SOCKET_PORT);
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://www.iana.org:443");
            BasicClassicHttpRequest request = new BasicClassicHttpRequest( "CONNECT", "www.iana.org:443");
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                // Ignore response
            }
        }
    }

    @AfterAll
    void after() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Ignore
        }
    }

}
