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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.FoomApplicationTest;
import org.kpax.winfoom.TestConstants;
import org.kpax.winfoom.config.ProxyConfig;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kpax.winfoom.TestConstants.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/2/2020
 */
@SpringBootTest(classes = FoomApplicationTest.class)
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(5)
class CustomProxyClientTests {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @MockBean
    private ProxyConfig proxyConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CredentialsProvider credentialsProvider;

    private HttpProxyServer proxyServer;

    @BeforeEach
    void beforeEach() {
        when(proxyConfig.getProxyHost()).thenReturn("localhost");
        when(proxyConfig.getProxyPort()).thenReturn(PROXY_PORT);
    }

    @BeforeAll
    void beforeClass() throws IOException {
        proxyServer = DefaultHttpProxyServer.bootstrap()
                .withAddress(new InetSocketAddress("localhost", PROXY_PORT))
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

    }

    @Test
    void tunnel_rightProxyAndCredentials_NoError()
            throws IOException, HttpException {
        HttpHost target = HttpHost.create("https://example.com");
        HttpHost proxy = new HttpHost(proxyConfig.getProxyHost(), proxyConfig.getProxyPort(), "http");
        credentialsProvider.clear();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USERNAME, PASSWORD));

        Tunnel tunnel = applicationContext.getBean(TunnelConnection.class)
                .open(proxy, target, HttpVersion.HTTP_1_1);
        tunnel.close();
    }

    @Test
    void tunnel_rightProxyWrongCredentials_TunnelRefusedException() {
        HttpHost target = HttpHost.create("https://example.com");
        HttpHost proxy = new HttpHost(proxyConfig.getProxyHost(), proxyConfig.getProxyPort(), "http");
        credentialsProvider.clear();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USERNAME, "wrong_pass"));
        assertThrows(org.apache.http.impl.execchain.TunnelRefusedException.class, () -> {
            Tunnel tunnel = applicationContext.getBean(TunnelConnection.class)
                    .open(proxy, target, HttpVersion.HTTP_1_1);
            tunnel.close();
        });
    }

    @Test
    void tunnel_wrongProxyRightCredentials_UnknownHostException() {
        HttpHost target = HttpHost.create("https://example.com");
        HttpHost proxy = new HttpHost("wronghost", proxyConfig.getProxyPort(), "http");
        credentialsProvider.clear();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USERNAME, PASSWORD));
        assertThrows(java.net.UnknownHostException.class, () -> {
            Tunnel tunnel = applicationContext.getBean(TunnelConnection.class)
                    .open(proxy, target, HttpVersion.HTTP_1_1);
            tunnel.close();
        });
    }

    @AfterAll
    void afterClass() {
        proxyServer.stop();
    }


}
