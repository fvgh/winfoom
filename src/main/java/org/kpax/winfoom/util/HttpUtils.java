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
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Eugen Covaci
 */
public final class HttpUtils {

    public static final String HTTP_CONNECT = "CONNECT";

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    private HttpUtils() {
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

    public static void tuneSocket(final Socket socket, int bufferSize) throws SocketException {
        socket.setTcpNoDelay(true);
        socket.setReceiveBufferSize(bufferSize);
        socket.setSendBufferSize(bufferSize);
    }

    public static void testProxyConfig(final UserConfig userConfig)
            throws IOException, CredentialException {
        logger.info("Test proxy config {}", userConfig);
        try (CloseableHttpClient httpClient = WinHttpClients.createDefault()) {
            HttpHost target = HttpHost.create(userConfig.getProxyTestUrl());
            HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
            RequestConfig config = RequestConfig.custom()
                    .setProxy(proxy)
                    .build();
            HttpGet request = new HttpGet("/");
            request.setConfig(config);
            logger.info("Executing request {} to {} via {}", target, proxy, request.getRequestLine());
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

    public static boolean isValidPort(int port) {
        return port > 0 && port < 65536;
    }

    public static ContentType getContentType(HttpRequest request) {
        Header contentTypeHeader = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        Validate.isTrue(contentTypeHeader != null, "No Content-Type header found");
        String[] tokens = contentTypeHeader.getValue().split(";");
        Validate.isTrue(tokens.length > 0, "Wrong content-type format");
        String contentType = tokens[0].trim();
        String charset = null;
        if (tokens.length > 1) {
            for (int i = 1; i < tokens.length; i++) {
                tokens[i] = tokens[i].trim();
                if (tokens[i].startsWith("charset=")) {
                    charset = tokens[i].substring("charset=".length());
                }
            }
        }
        return ContentType.create(contentType, charset);
    }

    public static void consumeEntity(HttpEntity httpEntity) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        httpEntity.writeTo(outStream);
        outStream.flush();
    }

}
