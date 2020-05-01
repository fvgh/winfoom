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
import org.kpax.winfoom.TestConstants;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.proxy.connection.ConnectionPoolingManager;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class HttpProxyClientConnectionTests {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int socketTimeout = 3; // seconds

    @MockBean
    private UserConfig userConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ConnectionPoolingManager connectionPoolingManager;

    private ServerSocket serverSocket;

    private HttpServer remoteServer;

    private HttpProxyServer remoteProxyServer;

    @BeforeEach
    void beforeEach() {
        when(userConfig.getProxyHost()).thenReturn("localhost");
        when(userConfig.getProxyPort()).thenReturn(PROXY_PORT);
        when(userConfig.getProxyType()).thenReturn(ProxyType.HTTP);
    }

    @BeforeAll
    void before() throws IOException {
        remoteProxyServer = DefaultHttpProxyServer.bootstrap()
                .withPort(PROXY_PORT)
                .withName("AuthenticatedUpstreamProxy")
                .withProxyAuthenticator(new ProxyAuthenticator() {
                    public boolean authenticate(String userName, String password) {
                        return userName.equals(USERNAME) && password.equals(PASSWORD);
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
                    throws IOException {
                response.setEntity(new StringEntity("12345"));
            }

        }).create();
        remoteServer.start();

        serverSocket = new ServerSocket(TestConstants.LOCAL_PROXY_PORT);
        connectionPoolingManager.start();
        final ServerSocket server = serverSocket;
        new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(socketTimeout * 1000);
                    new Thread(() -> {

                        // Handle this connection.
                        try {
                            applicationContext.getBean(ClientConnection.class, socket).handleRequest();
                        } catch (Exception e) {
                            logger.error("Error on handling connection", e);
                        }
                    }).start();
                } catch (Exception e) {
                    logger.error("Error on getting connection", e);
                }
            }
        }).start();

    }

    @Test
    @Order(1)
    void httpProxy_NonConnect_True() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
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
    void httpProxy_Connect_200OK() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
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
    void httpProxy_ConnectMalformedUri_500InternalServerError() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "/");
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());
                assertTrue(response.getStatusLine().getReasonPhrase().startsWith("Invalid HTTP host"));
            }
        }
    }

    @Test
    @Order(4)
    void httpProxy_NonConnectNoRemoteProxy_502BadGateway() throws IOException {
        remoteProxyServer.stop();
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
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
