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

import org.apache.http.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.FoomApplicationTest;
import org.kpax.winfoom.config.UserConfig;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kpax.winfoom.TestConstants.*;
import static org.mockito.Mockito.when;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/3/2020
 */
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = FoomApplicationTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@Timeout(10)
class SocketHandlerTests {

    @MockBean
    private LocalProxyServer localProxyServer;

    @MockBean
    private UserConfig userConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Autowired
    private ApplicationEventPublisher publisher;

    private AsynchronousServerSocketChannel serverSocket;

    private HttpServer remoteServer;

    private HttpProxyServer remoteProxyServer;

    @BeforeEach
    void beforeEach() {
        when(userConfig.getProxyHost()).thenReturn("localhost");
        when(userConfig.getProxyPort()).thenReturn(PROXY_PORT);
    }

    @BeforeAll
    void before() throws IOException {
        when(userConfig.getProxyHost()).thenReturn("localhost");
        when(userConfig.getProxyPort()).thenReturn(PROXY_PORT);
        remoteProxyServer = DefaultHttpProxyServer.bootstrap()
                .withPort(PROXY_PORT)
                .withName("AuthenticatedUpstreamProxy")
                .withProxyAuthenticator(new ProxyAuthenticator() {
                    public boolean authenticate(String userName, String password) {
                        if (userName.equals(USERNAME) && password.equals(PASSWORD)) {
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public String getRealm() {
                        return null;
                    }
                })
                .start();


        remoteServer = ServerBootstrap.bootstrap().registerHandler("/post", new HttpRequestHandler() {

            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                    throws HttpException, IOException {
                response.setEntity(new StringEntity("12345"));
            }

        }).create();

        remoteServer.start();

        serverSocket = AsynchronousServerSocketChannel.open()
                .bind(new InetSocketAddress(LOCAL_PROXY_PORT));
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
                    applicationContext.getBean(SocketHandler.class).bind(socketChanel).handleConnection();
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
    @Order(1)
    void request_NonConnect_True() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider).build()) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(localProxy)
                    .build();
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpPost request = new HttpPost("/post");
            request.setConfig(config);
            request.setEntity(new StringEntity("whatever"));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                assertEquals("12345", responseBody);
            }
        }
    }

    @Test
    @Order(2)
    void request_Connect_200OK() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider)
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Test
    @Order(3)
    void request_ConnectMalformedUri_500InternalServerError() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider)
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "/");
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
                assertEquals("Cannot parse CONNECT uri", response.getStatusLine().getReasonPhrase());
            }
        }
    }

    @Test
    @Order(4)
    void request_NonConnectNoRemoteProxy_502BadGateway() throws IOException {
        remoteProxyServer.stop();
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider).build()) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(localProxy)
                    .build();
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpPost request = new HttpPost("/post");
            request.setConfig(config);
            request.setEntity(new StringEntity("whatever"));

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_BAD_GATEWAY, response.getStatusLine().getStatusCode());
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
        remoteProxyServer.stop();
        remoteServer.stop();
    }

}
