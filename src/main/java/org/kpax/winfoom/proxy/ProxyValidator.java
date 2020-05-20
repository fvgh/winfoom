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
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.exception.InvalidProxySettingsException;
import org.kpax.winfoom.exception.PacFileException;
import org.kpax.winfoom.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.security.auth.login.CredentialException;
import java.io.IOException;
import java.net.*;
import java.util.Iterator;
import java.util.List;

/**
 * Responsible with proxy config validation.
 */
@Component
public class ProxyValidator {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyAutoConfig proxyAutoconfig;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    /**
     * Test the proxy settings.<br>
     *
     * @throws InvalidProxySettingsException
     */
    public void testProxyConfig()
            throws InvalidProxySettingsException {
        logger.info("Test proxy config {}", proxyConfig);
        ProxyType proxyType = proxyConfig.getProxyType();
        try {
            if (proxyConfig.isAutoConfig()) {
                List<ProxyInfo> proxyInfos = loadPacProxyInfos();
                for (Iterator<ProxyInfo> itr = proxyInfos.iterator(); itr.hasNext(); ) {
                    ProxyInfo proxyInfo = itr.next();
                    logger.info("Validate {}", proxyInfo);
                    ProxyType type = proxyInfo.getType();
                    try {
                        HttpHost host = proxyInfo.getProxyHost();
                        testProxyConfig(type,
                                host != null ? host.getHostName() : null,
                                host != null ? host.getPort() : -1);
                        break;
                    } catch (HttpHostConnectException | ConnectTimeoutException e) {
                        if (itr.hasNext()) {
                            proxyBlacklist.blacklist(proxyInfo);
                            logger.warn("Error on validating this proxy, will try the next one", e);
                        } else {
                            throw e;
                        }
                    }
                }
            } else {
                testProxyConfig(
                        proxyType,
                        proxyConfig.getProxyHost(),
                        proxyConfig.getProxyPort());
            }
        } catch (HttpHostConnectException | ConnectTimeoutException e) {
            throw new InvalidProxySettingsException("Wrong proxy host/port", e);
        }
    }

    private List<ProxyInfo> loadPacProxyInfos() throws InvalidProxySettingsException {
        try {
            proxyAutoconfig.loadScript();
            HttpHost testHost = HttpHost.create(proxyConfig.getProxyTestUrl());
            return proxyAutoconfig.findProxyForURL(new URI(testHost.toURI()));
        } catch (IOException e) {
            throw new InvalidProxySettingsException("Cannot load and parse the PAC file", e);
        } catch (PacFileException e) {
            throw new InvalidProxySettingsException("Invalid PAC file", e);
        } catch (URISyntaxException e) {
            throw new InvalidProxySettingsException("Invalid test URL", e);
        }
    }

    private void testProxyConfig(ProxyType proxyType,
                                 String proxyHost,
                                 int proxyPort)
            throws InvalidProxySettingsException, HttpHostConnectException, ConnectTimeoutException {

        logger.info("Test proxy with proxyType={}, proxyHost={}, proxyPort={}",
                proxyType,
                proxyHost,
                proxyPort);

        HttpClientBuilder httpClientBuilder;
        if (proxyType.isSocks()) {
            if (!proxyConfig.isAutoConfig()
                    && proxyType.isSocks5()
                    && StringUtils.isNotEmpty(proxyConfig.getProxyUsername())) {
                Authenticator.setDefault(new Authenticator() {
                    public PasswordAuthentication getPasswordAuthentication() {
                        String proxyPassword = proxyConfig.getProxyPassword();
                        return (new PasswordAuthentication(proxyConfig.getProxyUsername(),
                                proxyPassword != null ? proxyPassword.toCharArray() : new char[0]));
                    }
                });
            }
            ConnectionSocketFactory connectionSocketFactory = proxyType.isSocks4()
                    ? new Socks4ConnectionSocketFactory() : new SocksConnectionSocketFactory();
            Registry<ConnectionSocketFactory> factoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", connectionSocketFactory)
                    .register("https", connectionSocketFactory)
                    .build();

            httpClientBuilder =
                    HttpClients.custom().setConnectionManager(new PoolingHttpClientConnectionManager(factoryRegistry));
        } else {
            httpClientBuilder = WinHttpClients.custom();
        }

        try (CloseableHttpClient httpClient = httpClientBuilder.build()) {
            HttpHost target = HttpHost.create(proxyConfig.getProxyTestUrl());
            HttpGet request = new HttpGet("/");

            RequestConfig.Builder requestConfigBuilder = systemConfig.applyConfig(RequestConfig.custom());
            HttpClientContext context = HttpClientContext.create();

            if (proxyType.isHttp()) {
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                request.setConfig(requestConfigBuilder
                        .setProxy(proxy)
                        .build());
            } else {
                if (proxyType.isSocks()) {
                    context.setAttribute(HttpUtils.SOCKS_ADDRESS, new InetSocketAddress(proxyHost, proxyPort));
                }
                request.setConfig(requestConfigBuilder.build());
            }

            logger.info("Executing request {} to {}", request.getRequestLine(), target);

            try (CloseableHttpResponse response = httpClient.execute(target, request, context)) {
                StatusLine statusLine = response.getStatusLine();
                logger.info("Test response status {}", statusLine);
                if (statusLine.getStatusCode() < 300) {
                    logger.info("Test OK");
                } else if (statusLine.getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                    throw new InvalidProxySettingsException("Wrong user/password", new CredentialException(statusLine.toString()));
                } else if (statusLine.getStatusCode() == HttpStatus.SC_GATEWAY_TIMEOUT) {
                    throw new InvalidProxySettingsException("Cannot connect to the provided test URL", new HttpException(statusLine.toString()));
                } else {
                    throw new InvalidProxySettingsException("Something is wrong with the provided test URL", new HttpException(statusLine.toString()));
                }
            } catch (UnknownHostException e) {
                if (proxyType.isHttp()) {
                    throw new InvalidProxySettingsException("Wrong proxy host", e);
                } else {
                    throw new InvalidProxySettingsException("Cannot connect to the provided test URL", e);
                }
            }
        } catch (HttpHostConnectException | ConnectTimeoutException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidProxySettingsException("Error on validation proxy settings", e);
        }
    }
}
