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
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Arrays;
import java.util.List;

/**
 * This class handles the communication client <-> proxy facade <-> remote proxy. <br>
 *
 * @author Eugen Covaci
 */
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class SocketHandler {

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
    private static final List<String> DEFAULT_BANNED_HEADERS = Arrays.asList(
            HttpHeaders.PROXY_AUTHORIZATION);

    private final Logger logger = LoggerFactory.getLogger(SocketHandler.class);

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private HttpClientBuilderFactory clientBuilderFactory;

    @Autowired
    private ApplicationContext applicationContext;

    private AsynchronousSocketChannelWrapper localSocketChannel;

    SocketHandler bind(final AsynchronousSocketChannel socketChannel) {
        Assert.isNull(localSocketChannel, "Socket already bound!");
        this.localSocketChannel = new AsynchronousSocketChannelWrapper(socketChannel, systemConfig.getSocketChannelTimeout());
        return this;
    }

    void handleConnection() {
        logger.debug("Connection received");
        try {
            // Prepare request parsing (this is the client's request)
            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(
                    new HttpTransportMetricsImpl(),
                    LocalIOUtils.DEFAULT_BUFFER_SIZE,
                    LocalIOUtils.DEFAULT_BUFFER_SIZE,
                    MessageConstraints.DEFAULT,
                    ObjectFormat.UTF_8.newDecoder());
            inputBuffer.bind(localSocketChannel.getInputStream());

            // Parse the request (all but the message body )
            HttpRequest request = new DefaultHttpRequestParser(inputBuffer).parse();
            RequestLine requestLine = request.getRequestLine();

            logger.debug("Start processing request {}", requestLine);
            if (HttpUtils.HTTP_CONNECT.equalsIgnoreCase(requestLine.getMethod())) {
                applicationContext.getBean(ConnectRequestHandler.class).handleConnect(requestLine, localSocketChannel);
            } else {
                handleRequest(request, inputBuffer);
            }
            logger.debug("End processing request {}", requestLine);

        } catch (HttpException | ClientProtocolException | URISyntaxException e) {

            // There is something wrong with this request
            localSocketChannel.writelnError(HttpStatus.SC_BAD_REQUEST, e);
            logger.debug("Client error", e);
        } catch (ConnectException e) {

            // Cannot connect to the remote proxy
            localSocketChannel.writelnError(HttpStatus.SC_BAD_GATEWAY, e);
            logger.debug("Connection error", e);
        } catch (Exception e) {

            // Any other error
            localSocketChannel.writelnError(HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
            logger.debug("Local proxy error", e);
        } finally {
            LocalIOUtils.close(localSocketChannel);
        }

    }

    /**
     * Handle a non CONNECT request.
     *
     * @param request     The request to handle.
     * @param inputBuffer It buffers input data in an internal byte array for optimal input performance.
     * @throws IOException
     * @throws URISyntaxException
     */
    private void handleRequest(final HttpRequest request, final SessionInputBufferImpl inputBuffer)
            throws IOException, URISyntaxException {
        RequestLine requestLine = request.getRequestLine();
        logger.debug("Handle non-connect request {}", requestLine);

        if (request instanceof HttpEntityEnclosingRequest) {
            logger.debug("Set enclosing entity");
            RepeatableHttpEntity entity = new RepeatableHttpEntity(inputBuffer,
                    userConfig.getTempDirectory(),
                    request,
                    systemConfig.getInternalBufferLength());
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

        try (CloseableHttpClient httpClient = clientBuilderFactory.createHttpClientBuilder().build()) {

            // Extract URI
            URI uri = HttpUtils.parseUri(requestLine.getUri());
            HttpHost target = new HttpHost(uri.getHost(),
                    uri.getPort(),
                    uri.getScheme());

            // Execute the request
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {

                try {
                    handleResponse(response);
                } catch (Exception e) {
                    logger.debug("Error on handling non CONNECT response", e);
                }
            }
        } finally {
            if (request instanceof HttpEntityEnclosingRequest) {
                LocalIOUtils.close((RepeatableHttpEntity) ((HttpEntityEnclosingRequest) request).getEntity());
            }
        }
    }

    /**
     * Handles the Http response for non-CONNECT requests.<br>
     *
     * @param response The Http response.
     */
    private void handleResponse(final CloseableHttpResponse response) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Write status line: {}", response.getStatusLine());
        }
        localSocketChannel.write(response.getStatusLine());

        for (Header header : response.getAllHeaders()) {
            if (HttpHeaders.TRANSFER_ENCODING.equals(header.getName())) {
                // Strip 'chunked' from Transfer-Encoding header's value
                // since the response is not chunked
                String nonChunkedTransferEncoding = HttpUtils.stripChunked(header.getValue());
                if (StringUtils.isNotEmpty(nonChunkedTransferEncoding)) {
                    localSocketChannel.write(
                            HttpUtils.createHttpHeader(HttpHeaders.TRANSFER_ENCODING,
                                    nonChunkedTransferEncoding));
                    logger.debug("Add chunk-striped header response");
                } else {
                    logger.debug("Remove transfer encoding chunked header response");
                }
            } else {
                logger.debug("Write response header: {}", header);
                localSocketChannel.write(header);
            }
        }

        // Empty line marking the end
        // of header's section
        localSocketChannel.writeln();

        // Now write the request body, if any
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            logger.debug("Start writing entity content");
            entity.writeTo(localSocketChannel.getOutputStream());
            logger.debug("End writing entity content");

            // Make sure the entity is fully consumed
            EntityUtils.consume(entity);
        }

    }

}
