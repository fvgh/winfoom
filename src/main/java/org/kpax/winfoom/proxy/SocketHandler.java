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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.util.FoomIOUtils;
import org.kpax.winfoom.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * This class handles the communication client <-> proxy facade <-> remote proxy. <br>
 *
 * @author Eugen Covaci
 */
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class SocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SocketHandler.class);

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

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private ProxyContext proxyContext;

    @Autowired
    private CustomProxyClient proxyClient;

    @Autowired
    private HttpClientBuilder httpClientBuilder;

    private AsynchronousSocketChannelWrapper localSocketChannel;


    SocketHandler bind(AsynchronousSocketChannel socketChannel) {
        Assert.isNull(localSocketChannel, "Socket already binded!");
        this.localSocketChannel = new AsynchronousSocketChannelWrapper(socketChannel);
        return this;
    }

    void handleRequest() {
        logger.debug("Connection received");
        try {
            // Prepare request parsing (this is the client's request)
            HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(metrics,
                    FoomIOUtils.DEFAULT_BUFFER_SIZE);
            inputBuffer.bind(localSocketChannel.getInputStream());

            // Parse the request (all but the message body )
            HttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inputBuffer);
            HttpRequest request;
            try {
                request = requestParser.parse();
            } catch (Exception e) {
                if (!(e instanceof ConnectionClosedException)) {
                    localSocketChannel.writelnError(e);
                }
                throw e;
            }

            RequestLine requestLine = request.getRequestLine();
            logger.debug("Start processing request line {}", requestLine);

            if (HttpUtils.HTTP_CONNECT.equalsIgnoreCase(requestLine.getMethod())) {
                handleConnect(requestLine);
            } else {
                handleNonConnectRequest(request, inputBuffer);
            }

            logger.debug("End processing request line {}", requestLine);
        } catch (Exception e) {
            if (HttpUtils.isClientException(e.getClass())) {
                logger.debug("Client error", e);
            } else {
                logger.error("Error on handling local socket connection", e);
            }
        } finally {
            FoomIOUtils.close(localSocketChannel);
        }
    }

    /**
     * Creates a tunnel through proxy, then let the client and the remote proxy
     * communicate via the local socket channel instance.
     *
     * @param requestLine The first line of the request.
     * @throws Exception
     */
    private void handleConnect(RequestLine requestLine) throws Exception {
        logger.debug("Handle proxy connect request");

        Pair<String, Integer> hostPort;
        try {

            // Get host and port
            hostPort = HttpUtils.parseConnectUri(requestLine.getUri());
        } catch (HttpException e) {

            // We give back to the client
            // a Bad Request status line
            // since the connect line is bad.
            localSocketChannel.writelnError(e);
            throw e;
        }

        HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
        HttpHost target = new HttpHost(hostPort.getLeft(), hostPort.getRight());
        Socket socket = null;
        try {
            // Creates a tunnel through proxy.
            socket = proxyClient.tunnel(proxy, target, requestLine.getProtocolVersion(),
                    localSocketChannel);

            final OutputStream socketOutputStream = socket.getOutputStream();
            Future<?> copyToSocketFuture = proxyContext.executeAsync(
                    () -> FoomIOUtils.copyQuietly(localSocketChannel.getInputStream(),
                            socketOutputStream));
            FoomIOUtils.copyQuietly(socket.getInputStream(), localSocketChannel.getOutputStream());
            if (!copyToSocketFuture.isDone()) {
                try {

                    // Wait for async copy to finish
                    copyToSocketFuture.get();
                } catch (ExecutionException e) {

                    // If the cause is an IOException
                    // we throw it as it is, otherwise
                    // we wrap it into an IOException
                    if (e.getCause() instanceof IOException) {
                        throw (IOException) e.getCause();
                    }
                    throw new IOException("Error on writing to socket", e.getCause());
                } catch (Exception e) {
                    throw new IOException("Error on writing to socket", e);
                }
            }
        } catch (org.apache.http.impl.execchain.TunnelRefusedException tre) {
            try {
                HttpResponse errorResponse = tre.getResponse();
                if (errorResponse != null) {
                    StatusLine errorStatusLine = errorResponse.getStatusLine();
                    logger.debug("errorStatusLine {}", errorStatusLine);

                    localSocketChannel.write(errorStatusLine);

                    logger.debug("Start writing error headers");
                    for (Header header : errorResponse.getAllHeaders()) {
                        localSocketChannel.write(header);
                    }

                    // Empty line between headers and the body
                    localSocketChannel.writeln();

                    HttpEntity entity = errorResponse.getEntity();
                    if (entity != null) {
                        logger.debug("Start writing error entity content");
                        entity.writeTo(localSocketChannel.getOutputStream());
                        logger.debug("End writing error entity content");
                    }
                    EntityUtils.consume(entity);
                } else {

                    // No response from the remote proxy,
                    // therefore we give back to the client
                    // an 502 Bad Gateway
                    localSocketChannel.writelnError(HttpStatus.SC_BAD_GATEWAY, tre);
                }
            } catch (Exception e) {
                logger.error("Error on sending error response", e);
            }
        } catch (IOException e) {
            logger.debug("Error on reading/writing through proxy tunnel", e);
        } catch (Exception e) {
            logger.error("Error on creating/handling proxy tunnel", e);
        } finally {
            FoomIOUtils.close(socket);
        }
    }

    /**
     * Handle a non CONNECT request.
     *
     * @param request     The request to handle.
     * @param inputBuffer It buffers input data in an internal byte array for optimal input performance.
     * @throws Exception
     */
    private void handleNonConnectRequest(HttpRequest request, SessionInputBufferImpl inputBuffer) throws Exception {
        RequestLine requestLine = request.getRequestLine();
        logger.debug("Handle non-connect request {}", requestLine);

        // Set our streaming entity
        if (request instanceof BasicHttpEntityEnclosingRequest) {
            logger.debug("Create and set PseudoBufferedHttpEntity instance");
            HttpEntity entity = new PseudoBufferedHttpEntity(inputBuffer, request,
                    systemConfig.getInternalBufferLength());
            ((BasicHttpEntityEnclosingRequest) request).setEntity(entity);
        } else {
            logger.debug("No enclosing entity");
        }

        // Create the CloseableHttpClient instance
        CloseableHttpClient httpClient = httpClientBuilder.build();

        try {
            List<String> bannedHeaders = request instanceof BasicHttpEntityEnclosingRequest ?
                    ENTITY_BANNED_HEADERS : DEFAULT_BANNED_HEADERS;
            // Remove banned headers
            for (Header header : request.getAllHeaders()) {
                if (bannedHeaders.contains(header.getName())) {
                    request.removeHeader(header);
                    logger.debug("Request header {} removed", header);
                } else {
                    logger.debug("Allow request header {}", header);
                }
            }

            // Execute the request
            try {
                URI uri = HttpUtils.parseUri(requestLine.getUri());
                HttpHost target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
                CloseableHttpResponse response;
                try {
                    response = httpClient.execute(target, request);
                } catch (Exception e) {
                    localSocketChannel.writelnError(e);
                    throw e;
                }
                try {
                    String statusLine = response.getStatusLine().toString();
                    logger.debug("Response status line: {}", statusLine);

                    localSocketChannel.write(statusLine);

                    logger.debug("Done writing status line, now write response headers");

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
                            localSocketChannel.write(header);
                            logger.debug("Done writing response header: {}", header);
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

                } finally {
                    FoomIOUtils.close(response);
                }

            } catch (org.apache.http.client.ClientProtocolException e) {
                logger.debug("Error in the HTTP protocol", e);
            } catch (Throwable e) {
                logger.error("Error on executing HTTP request", e);
            }
            logger.debug("End handling non-connect request {}", requestLine);
        } finally {
            FoomIOUtils.close(httpClient);
        }
    }

}
