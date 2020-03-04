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

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.kpax.winfoom.FoomApplicationTest;
import org.kpax.winfoom.config.UserConfig;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.mockito.Mockito.when;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/2/2020
 */
@SpringBootTest(classes = FoomApplicationTest.class)
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
public class CustomProxyClientTests {

    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 80;

    private static final String USERNAME = "user";
    private static final String PASSWORD = "pass";

    @MockBean
    private UserConfig userConfig;

    @Autowired
    private CustomProxyClient customProxyClient;

    @Autowired
    private CredentialsProvider credentialsProvider;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    private HttpProxyServer proxyServer;

    @Before
    public void before() {

        when(userConfig.getProxyHost()).thenReturn("localhost");
        when(userConfig.getProxyPort()).thenReturn(PROXY_PORT);

        proxyServer = DefaultHttpProxyServer.bootstrap()
                .withAddress(new InetSocketAddress(PROXY_HOST, PROXY_PORT))
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
    }


    @Test
    public void tunnel_rightProxyAndCredentials_NoError() throws IOException, HttpException {
        HttpHost target = HttpHost.create("https://example.com");
        HttpHost proxy = new HttpHost("localhost", 80, "http");
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("user", "pass"));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Socket socket = customProxyClient.tunnel(proxy, target, HttpVersion.HTTP_1_1, outputStream);
        System.out.println(new String(outputStream.toByteArray()));
        socket.close();
    }

    @Test(expected = org.apache.http.impl.execchain.TunnelRefusedException.class)
    public void tunnel_rightProxyWrongCredentials_TunnelRefusedException() throws IOException, HttpException {
        HttpHost target = HttpHost.create("https://example.com");
        HttpHost proxy = new HttpHost("localhost", 80, "http");
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("user", "wrong_pass"));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Socket socket = customProxyClient.tunnel(proxy, target, HttpVersion.HTTP_1_1, outputStream);
        System.out.println(new String(outputStream.toByteArray()));
        socket.close();
    }


    @Test(expected = java.net.UnknownHostException.class)
    public void tunnel_wrongProxyRightCredentials_UnknownHostException() throws IOException, HttpException {
        HttpHost target = HttpHost.create("https://example.com");
        HttpHost proxy = new HttpHost("wronghost", 80, "http");
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("user", "pass"));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Socket socket = customProxyClient.tunnel(proxy, target, HttpVersion.HTTP_1_1, outputStream);
        System.out.println(new String(outputStream.toByteArray()));
        socket.close();
    }

    @After
    public void after() {
        proxyServer.stop();
    }
}
