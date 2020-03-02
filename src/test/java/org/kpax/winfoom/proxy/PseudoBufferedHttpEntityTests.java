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

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.junit.*;
import org.junit.rules.Timeout;
import org.kpax.winfoom.util.CrlfWriter;
import org.kpax.winfoom.util.FoomIOUtils;
import org.kpax.winfoom.util.HttpUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 2/29/2020
 */
public class PseudoBufferedHttpEntityTests {

    private final String entityRepeatableHeader = "Entity-Repeatable";

    private final String echoContentHeader = "Echo-content";

    private final int port = 3128;

    private AsynchronousServerSocketChannel serverSocket;

    private int bufferSize;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    @Before
    public void before() throws IOException {
        serverSocket = AsynchronousServerSocketChannel.open()
                .bind(new InetSocketAddress(port));
        serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

            public void completed(AsynchronousSocketChannel socketChanel, Void att) {
                try {
                    // accept the next connection
                    serverSocket.accept(null, this);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Handle this connection.
                try (AsynchronousSocketChannelWrapper localSocketChannel = new AsynchronousSocketChannelWrapper(socketChanel)) {
                    HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
                    SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(metrics,
                            FoomIOUtils.DEFAULT_BUFFER_SIZE);
                    inputBuffer.bind(localSocketChannel.getInputStream());

                    HttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inputBuffer);
                    HttpRequest request = requestParser.parse();

                    PseudoBufferedHttpEntity entity = new PseudoBufferedHttpEntity(inputBuffer, request,
                            bufferSize);
                    ((BasicHttpEntityEnclosingRequest) request).setEntity(entity);

                    CrlfWriter crlfWriter = new CrlfWriter(localSocketChannel.getOutputStream());
                    crlfWriter
                            .write("HTTP/1.1 200 OK")
                            .write(HttpUtils.createHttpHeader(entityRepeatableHeader,
                                    String.valueOf(entity.isRepeatable())));

                    if (request.containsHeader(echoContentHeader)) {
                        crlfWriter
                                .write(request.getFirstHeader(HTTP.CONTENT_LEN))
                                .write(request.getFirstHeader(HTTP.CONTENT_TYPE));
                        crlfWriter.writeEmptyLine();
                        entity.getContent().transferTo(localSocketChannel.getOutputStream());
                    } else {
                        crlfWriter.write(HttpUtils.createHttpHeader(HTTP.CONTENT_LEN, "0"));
                        crlfWriter.writeEmptyLine();
                        EntityUtils.consume(entity);
                    }
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
    public void repeatable_BufferLessThanContentLength_False() throws IOException {
        this.bufferSize = 1;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            request.setEntity(new StringEntity("12345"));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                Assert.assertEquals(response.getFirstHeader("Entity-Repeatable").getValue(), "false");
            }
        }
    }

    @Test
    public void repeatable_BufferEqualsContentLength_True() throws IOException {
        this.bufferSize = 5;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            request.setEntity(new StringEntity("12345"));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                Assert.assertEquals(response.getFirstHeader("Entity-Repeatable").getValue(), "true");
            }
        }
    }

    @Test
    public void repeatable_BufferBiggerThanContentLength_True() throws IOException {
        this.bufferSize = 7;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            request.setEntity(new StringEntity("12345"));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                Assert.assertEquals(response.getFirstHeader("Entity-Repeatable").getValue(), "true");
            }
        }
    }

    @Test
    public void repeatable_NegativeContentLengthBufferBiggerThanContentLength_False() throws IOException {
        this.bufferSize = 10;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            request.setEntity(new InputStreamEntity(new ByteArrayInputStream("12345".getBytes())));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                Assert.assertEquals(response.getFirstHeader("Entity-Repeatable").getValue(), "false");
            }
        }
    }

    @Test
    public void repeatable_NegativeContentLengthBufferLessThanContentLength_False() throws IOException {
        this.bufferSize = 2;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            request.setEntity(new InputStreamEntity(new ByteArrayInputStream("12345".getBytes())));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                Assert.assertEquals(response.getFirstHeader("Entity-Repeatable").getValue(), "false");
            }
        }
    }


    @Test
    public void repeatable_NegativeContentLengthNoAvailableData_False() throws IOException {
        this.bufferSize = 1024;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                EntityUtils.consume(response.getEntity());
                Assert.assertEquals(response.getFirstHeader("Entity-Repeatable").getValue(), "true");
            }
        }
    }


    @Test
    public void body_IsRepeatableWithEcho_True() throws IOException {
        this.bufferSize = 7;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            request.addHeader(echoContentHeader, "true");
            String requestBody = "12345";
            request.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                String responseBody = EntityUtils.toString(response.getEntity());
                Assert.assertEquals(requestBody, responseBody);
            }
        }
    }

    @Test
    public void body_NotRepeatableWithEcho_True() throws IOException {
        this.bufferSize = 2;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            request.addHeader(echoContentHeader, "true");
            String requestBody = "12345";
            request.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                HttpEntity responseEntity = response.getEntity();
                String responseBody = EntityUtils.toString(responseEntity);
                Assert.assertEquals(requestBody, responseBody);
            }
        }
    }

    @Test
    public void body_IsRepeatableNoEcho_True() throws IOException {
        this.bufferSize = 7;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            String requestBody = "12345";
            request.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                String responseBody = EntityUtils.toString(response.getEntity());
                Assert.assertNotEquals(requestBody, responseBody);
            }
        }
    }

    @Test
    public void body_NotRepeatableNoEcho_True() throws IOException {
        this.bufferSize = 2;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpHost target = HttpHost.create("http://localhost:" + port);
            HttpPost request = new HttpPost("/");
            String requestBody = "12345";
            request.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
                String responseBody = EntityUtils.toString(response.getEntity());
                Assert.assertNotEquals(requestBody, responseBody);
            }
        }
    }


    @After
    public void after() throws IOException {
        serverSocket.close();
    }

}
