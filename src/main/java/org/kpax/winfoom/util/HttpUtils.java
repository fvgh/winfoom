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

package org.kpax.winfoom.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.EnglishReasonPhraseCatalog;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.WinHttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;
import org.kpax.winfoom.config.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.CredentialException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Eugen Covaci
 */
public final class HttpUtils {

    public static final String HTTP_CONNECT = "CONNECT";

    private static final List<Class> CLIENT_EXCEPTIONS = new ArrayList<>();

    private static final List<Class> GATEWAY_EXCEPTIONS = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    static {
        CLIENT_EXCEPTIONS.add(org.apache.http.HttpException.class);
        CLIENT_EXCEPTIONS.add(org.apache.http.client.ClientProtocolException.class);
        CLIENT_EXCEPTIONS.add(org.apache.http.ConnectionClosedException.class);

        GATEWAY_EXCEPTIONS.add(org.apache.http.conn.HttpHostConnectException.class);
        GATEWAY_EXCEPTIONS.add(java.net.ConnectException.class);
    }

    private HttpUtils() {
    }

    public static Pair<String, Integer> parseConnectUri(String uri) throws HttpException {
        try {
            String[] split = uri.split(":");
            return new ImmutablePair<>(split[0], Integer.parseInt(split[1]));
        } catch (Exception e) {
            throw new HttpException("Cannot parse CONNECT uri", e);
        }
    }

    public static URI parseUri(String url) throws URISyntaxException {
        int index = url.indexOf("?");
        if (index > -1) {
            URIBuilder uriBuilder = new URIBuilder(url.substring(0, index));
            List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(url.substring(index + 1), StandardCharsets.UTF_8);
            uriBuilder.addParameters(nameValuePairs);
            return uriBuilder.build();
        }
        return new URIBuilder(url).build();
    }

    public static String stripChunked(String value) {
        return Arrays.stream(value.split(","))
                .filter((item) -> !HTTP.CHUNK_CODING.equalsIgnoreCase(item))
                .collect(Collectors.joining(","));
    }

    public static Optional<Header> getFirstHeader(HttpRequest request, String name) {
        return Optional.ofNullable(request.getFirstHeader(name));
    }

    public static Optional<String> getFirstHeaderValue(HttpRequest request, String name) {
        return getFirstHeader(request, name).map(NameValuePair::getValue);
    }

    public static long getContentLength(HttpRequest request) {
        return getFirstHeaderValue(request, HttpHeaders.CONTENT_LENGTH).map(Long::parseLong).orElse(-1L);
    }

    public static Header createHttpHeader(String name, String value) {
        return new BasicHeader(name, value);
    }

    public static Socket tuneSocket(final Socket socket, int bufferSize) throws SocketException {
        socket.setTcpNoDelay(true);
        socket.setReceiveBufferSize(bufferSize);
        socket.setSendBufferSize(bufferSize);
        return socket;
    }

    public static void testProxyConfig(final UserConfig userConfig)
            throws IOException, CredentialException {
        try (CloseableHttpClient httpClient = WinHttpClients.createDefault()) {
            HttpHost target = HttpHost.create(userConfig.getProxyTestUrl());
            HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort(), "http");
            RequestConfig config = RequestConfig.custom()
                    .setProxy(proxy)
                    .build();
            HttpGet request = new HttpGet("/");
            request.setConfig(config);
            logger.info("Executing request " + request.getRequestLine() + " to " + target + " via " + proxy);
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                StatusLine statusLine = response.getStatusLine();
                logger.info("Test response status {}", statusLine);
                if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
                    logger.info("Test OK");
                } else if (statusLine.getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                    throw new CredentialException(statusLine.getReasonPhrase());
                }
            }
        } catch (Exception e) {
            logger.error("Error on testing proxy config", e);
            throw e;
        }
    }

    public static BasicStatusLine toStatusLine(int httpCode) {
        return toStatusLine(HttpVersion.HTTP_1_1, httpCode, null);
    }

    public static BasicStatusLine toStatusLine(ProtocolVersion protocolVersion, int httpCode) {
        return toStatusLine(protocolVersion, httpCode, null);
    }

    public static BasicStatusLine toStatusLine(int httpCode, String reasonPhrase) {
        return toStatusLine(HttpVersion.HTTP_1_1, httpCode, reasonPhrase);
    }

    public static BasicStatusLine toStatusLine(ProtocolVersion protocolVersion, int httpCode, String reasonPhrase) {
        Validate.notNull(protocolVersion, "protocolVersion cannot be null");
        return new BasicStatusLine(protocolVersion, httpCode,
                StringUtils.isEmpty(reasonPhrase) ?
                        EnglishReasonPhraseCatalog.INSTANCE.getReason(httpCode, Locale.ENGLISH) : reasonPhrase);
    }

    public static boolean isClientException(Class<? extends Exception> e) {
        return CLIENT_EXCEPTIONS.stream().anyMatch(c -> c.isAssignableFrom(e));
    }

    public static boolean isGatewayException(Class<? extends Exception> e) {
        return GATEWAY_EXCEPTIONS.stream().anyMatch(c -> c.isAssignableFrom(e));
    }

}
