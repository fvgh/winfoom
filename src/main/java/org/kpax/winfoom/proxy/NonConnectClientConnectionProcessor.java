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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.InputOutputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Process any type of non-CONNECT request for any type of proxy.
 *
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/13/2020
 */
@Component
class NonConnectClientConnectionProcessor implements ClientConnectionProcessor {

    /**
     * These headers will be removed from client's response if there is an enclosing
     * entity.
     */
    private static final List<String> ENTITY_BANNED_HEADERS = Arrays.asList(
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CONTENT_ENCODING,
            HttpHeaders.PROXY_AUTHORIZATION);
    /**
     * These headers will be removed from client's response if there is no enclosing
     * entity (it means the request has no body).
     */
    private static final List<String> DEFAULT_BANNED_HEADERS = Collections.singletonList(
            HttpHeaders.PROXY_AUTHORIZATION);

    private final Logger logger = LoggerFactory.getLogger(NonConnectClientConnectionProcessor.class);

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private HttpClientBuilderFactory clientBuilderFactory;

    @Override
    public void process(final ClientConnection clientConnection, final ProxyInfo proxyInfo)
            throws IOException, URISyntaxException {
        logger.debug("Handle non-connect request");
        HttpRequest request = clientConnection.getHttpRequest();

        if (!clientConnection.isRequestPrepared()) {

            // Prepare the request for execution
            AbstractHttpEntity entity;
            if (request instanceof HttpEntityEnclosingRequest) {
                logger.debug("Set enclosing entity");
                if (proxyConfig.getProxyType().isSocks()) {

                    // There is no need for caching since
                    // SOCKS communication is one step only
                    entity = new InputStreamEntity(clientConnection.getInputStream(),
                            HttpUtils.getContentLength(request),
                            HttpUtils.getContentType(request));
                } else {
                    entity = new RepeatableHttpEntity(clientConnection.getSessionInputBuffer(),
                            proxyConfig.getTempDirectory(),
                            request,
                            systemConfig.getInternalBufferLength());
                }

                Header transferEncoding = request.getFirstHeader(HTTP.TRANSFER_ENCODING);
                if (transferEncoding != null
                        && StringUtils.containsIgnoreCase(transferEncoding.getValue(), HTTP.CHUNK_CODING)) {
                    logger.debug("Mark entity as chunked");
                    entity.setChunked(true);

                    // Apache HttpClient adds a Transfer-Encoding header's chunk directive
                    // so remove or strip the existent one of chunk directive
                    request.removeHeader(transferEncoding);
                    String nonChunkedTransferEncoding = HttpUtils.stripChunked(transferEncoding.getValue());
                    if (StringUtils.isNotEmpty(nonChunkedTransferEncoding)) {
                        request.addHeader(
                                HttpUtils.createHttpHeader(HttpHeaders.TRANSFER_ENCODING,
                                        nonChunkedTransferEncoding));
                        logger.debug("Add chunk-striped request header");
                    } else {
                        logger.debug("Remove transfer encoding chunked request header");
                    }

                }
                ((HttpEntityEnclosingRequest) request).setEntity(entity);
            } else {
                logger.debug("No enclosing entity");
            }

            // Remove banned headers
            List<String> bannedHeaders = request instanceof HttpEntityEnclosingRequest ?
                    ENTITY_BANNED_HEADERS : DEFAULT_BANNED_HEADERS;
            for (Header header : request.getAllHeaders()) {
                if (bannedHeaders.contains(header.getName())) {
                    request.removeHeader(header);
                    logger.debug("Request header {} removed", header);
                } else {
                    logger.debug("Allow request header {}", header);
                }
            }

            clientConnection.requestPrepared();
        }

        try (CloseableHttpClient httpClient = clientBuilderFactory.createClientBuilder(proxyInfo).build()) {

            // Extract URI
            URI uri = HttpUtils.parseUri(request.getRequestLine().getUri());
            HttpHost target = new HttpHost(uri.getHost(),
                    uri.getPort(),
                    uri.getScheme());

            HttpClientContext context = HttpClientContext.create();
            if (proxyInfo.getType().isSocks()) {
                InetSocketAddress proxySocketAddress = new InetSocketAddress(proxyInfo.getProxyHost().getHostName(),
                        proxyInfo.getProxyHost().getPort());
                context.setAttribute(HttpUtils.SOCKS_ADDRESS, proxySocketAddress);
            }

            // Execute the request
            try (CloseableHttpResponse response = httpClient.execute(target, request, context)) {

                try {
                    handleResponse(response, clientConnection);
                } catch (Exception e) {
                    logger.debug("Error on handling non CONNECT response", e);
                }
            }
        } finally {
            if (clientConnection.isLastResort()) {
                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    if (entity instanceof AutoCloseable) {
                        InputOutputs.close((AutoCloseable) entity);
                    }
                }
            }
        }
    }

    /**
     * Handles the Http response for non-CONNECT requests.<br>
     *
     * @param response The Http response.
     */
    private void handleResponse(final CloseableHttpResponse response,
                                ClientConnection clientConnection) throws IOException {
        StatusLine statusLine = response.getStatusLine();
        if (logger.isDebugEnabled()) {
            logger.debug("Write status line: {}", statusLine);
        }
        clientConnection.write(statusLine);

        for (Header header : response.getAllHeaders()) {
            if (HttpHeaders.TRANSFER_ENCODING.equals(header.getName())) {

                // Strip 'chunked' from Transfer-Encoding header's value
                // since the response is not chunked
                String nonChunkedTransferEncoding = HttpUtils.stripChunked(header.getValue());
                if (StringUtils.isNotEmpty(nonChunkedTransferEncoding)) {
                    clientConnection.write(
                            HttpUtils.createHttpHeader(HttpHeaders.TRANSFER_ENCODING,
                                    nonChunkedTransferEncoding));
                    logger.debug("Add chunk-striped header response");
                } else {
                    logger.debug("Remove transfer encoding chunked header response");
                }
            } else {
                logger.debug("Write response header: {}", header);
                clientConnection.write(header);
            }
        }

        // Empty line marking the end
        // of header's section
        clientConnection.writeln();

        // Now write the request body, if any
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            logger.debug("Start writing entity content");
            entity.writeTo(clientConnection.getOutputStream());
            logger.debug("End writing entity content");

            // Make sure the entity is fully consumed
            EntityUtils.consume(entity);
        }

    }
}
