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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.util.CrlfFormat;
import org.kpax.winfoom.util.Functions;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

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
     * entity (it means the response has no body).
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

    private AsynchronousSocketChannelWrapper localSocketChannel;

    SocketHandler bind(AsynchronousSocketChannel socketChannel) {
        Assert.isNull(localSocketChannel, "Socket already binded!");
        this.localSocketChannel = new AsynchronousSocketChannelWrapper(socketChannel, systemConfig.getSocketChannelTimeout());
        return this;
    }

    void handleRequest() {
        logger.debug("Connection received");
        try {
            // Prepare request parsing (this is the client's request)
            HttpTransportMetricsImpl metrics = new HttpTransportMetricsImpl();
            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(metrics, LocalIOUtils.DEFAULT_BUFFER_SIZE);
            inputBuffer.bind(localSocketChannel.getInputStream());

            // Parse the request (all but the message body )
            HttpMessageParser<HttpRequest> requestParser = new DefaultHttpRequestParser(inputBuffer);
            HttpRequest request;
            try {
                request = requestParser.parse();
            } catch (HttpException e) {
                localSocketChannel.getOutputStream().write(CrlfFormat.format(HttpUtils.toStatusLine(HttpStatus.SC_BAD_REQUEST)));
                throw e;
            } catch (ConnectionClosedException e) {
                throw e;
            } catch (Exception e) {
                localSocketChannel.getOutputStream().write(CrlfFormat.format(HttpUtils.toInternalServerErrorStatusLine()));
                throw e;
            }

            RequestLine requestLine = request.getRequestLine();
            logger.debug("Start processing request line {}", requestLine);

            if (HttpUtils.HTTP_CONNECT.equalsIgnoreCase(requestLine.getMethod())) {
                handleConnect(requestLine);
            } else {
                handleRequest(request, inputBuffer);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("End processing request line {}", requestLine);
            }
        } catch (ConnectionClosedException e) {

            // The connection had been closed unexpectedly.
            // Not a real error, therefore we only debug it.
            logger.debug(e.getMessage(), e);
        } catch (Throwable e) {
            logger.error("Error on handling local socket connection", e);
        } finally {
            LocalIOUtils.close(localSocketChannel);
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

        HttpHost proxy;
        HttpHost target;
        try {
            URI uri = HttpUtils.parseConnectUri(requestLine.getUri());
            proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
            target = new HttpHost(uri.getHost(), uri.getPort());
        } catch (Exception e) {

            // We give back to the client
            // a Bad Request status line
            // since the connect line is bad.
            localSocketChannel.getOutputStream().write(
                    CrlfFormat.format(HttpUtils.toStatusLine(
                            requestLine.getProtocolVersion(), HttpStatus.SC_BAD_REQUEST)));
            throw e;
        }

        Socket socket = null;
        Future<?> copyToSocketFuture = null;
        try {
            // Creates a tunnel through proxy.
            socket = proxyClient.tunnel(proxy, target, requestLine.getProtocolVersion(),
                    localSocketChannel.getOutputStream());
            final OutputStream socketOutputStream = socket.getOutputStream();
            copyToSocketFuture = proxyContext.executeAsync(() -> LocalIOUtils.copyQuietly(localSocketChannel.getInputStream(),
                    socketOutputStream));
            LocalIOUtils.copyQuietly(socket.getInputStream(), localSocketChannel.getOutputStream());
        } catch (org.apache.http.impl.execchain.TunnelRefusedException tre) {
            try {
                HttpResponse errorResponse = tre.getResponse();
                if (errorResponse != null) {
                    StatusLine errorStatusLine = errorResponse.getStatusLine();
                    logger.debug("errorStatusLine {}", errorStatusLine);

                    OutputStream localOutputStream = localSocketChannel.getOutputStream();
                    localOutputStream.write(CrlfFormat.format(errorStatusLine.toString()));

                    logger.debug("Start writing error headers");
                    for (Header header : errorResponse.getAllHeaders()) {
                        localOutputStream.write(CrlfFormat.format(header.toString()));
                    }

                    // Empty line between headers and the body
                    localOutputStream.write(CrlfFormat.CRLF.getBytes());

                    HttpEntity entity = errorResponse.getEntity();
                    if (entity != null) {
                        logger.debug("Start writing error entity content");
                        entity.writeTo(localOutputStream);
                        logger.debug("End writing error entity content");
                    }
                    EntityUtils.consume(entity);
                } else {

                    // No remote response, therefore we give back
                    // to the client an Internal Server Error status line
                    localSocketChannel.getOutputStream().write(
                            CrlfFormat.format(HttpUtils.toInternalServerErrorStatusLine(
                                    requestLine.getProtocolVersion())));
                }
            } catch (Exception e) {
                logger.error("Error on sending error response", e);
            }
        } catch (Exception e) {
            logger.error("Error on creating/handling proxy tunnel", e);
        } finally {
            if (copyToSocketFuture != null) {
                try {

                    // Wait for copy to finish
                    // before closing the socket
                    copyToSocketFuture.get();
                } catch (ExecutionException e) {
                    logger.debug("Error on writing to socket", e.getCause());
                } catch (Exception e) {
                    logger.debug("Error on writing to socket", e);
                }
            }
            LocalIOUtils.close(socket);
        }
    }

    /**
     * Handle a non CONNECT request.
     *
     * @param request     The request to handle.
     * @param inputBuffer It buffers input data in an internal byte array for optimal input performance.
     * @throws Exception
     */
    private void handleRequest(HttpRequest request, SessionInputBufferImpl inputBuffer) throws Exception {
        RequestLine requestLine = request.getRequestLine();
        logger.debug("Handle non-connect request {}", requestLine);

        // Set our streaming entity
        if (request instanceof BasicHttpEntityEnclosingRequest) {
            logger.debug("Create and set PseudoBufferedHttpEntity instance");
            HttpEntity entity = new PseudoBufferedHttpEntity(inputBuffer, request,
                    systemConfig.getInternalBufferLength());
            ((BasicHttpEntityEnclosingRequest) request).setEntity(entity);
        } else if (logger.isDebugEnabled()) {
            logger.debug("No enclosing entity");
        }

        final boolean retryRequest = !(request instanceof BasicHttpEntityEnclosingRequest)
                || ((BasicHttpEntityEnclosingRequest) request).getEntity().isRepeatable();
        logger.debug("retryRequest {} ", retryRequest);

        URI uri = HttpUtils.parseUri(requestLine.getUri());
        CloseableHttpClient httpClient = proxyContext.createHttpClient(retryRequest);

        try {
            List<String> bannedHeaders = request instanceof BasicHttpEntityEnclosingRequest ?
                    ENTITY_BANNED_HEADERS : DEFAULT_BANNED_HEADERS;
            // Remove banned headers
            for (Header header : request.getAllHeaders()) {
                if (bannedHeaders.contains(header.getName())) {
                    request.removeHeader(header);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Request header {} removed", header);
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Allow request header {}", header);
                    }
                }
            }

            // Execute the request
            try {
                HttpHost target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
                OutputStream outputStream = localSocketChannel.getOutputStream();
                CloseableHttpResponse response;
                try {
                    if (retryRequest) {
                        response = Functions.repeat(() -> httpClient.execute(target, request),
                                        (t) -> t.getStatusLine().getStatusCode() != HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
                                        systemConfig.getRepeatsOnFailure()).orElseThrow();
                    } else {
                        response = httpClient.execute(target, request);
                    }
                } catch (Exception e) {

                    // No remote response, therefore we give back
                    // to the client an Internal Server Error status line
                    localSocketChannel.getOutputStream().write(CrlfFormat.format(
                            HttpUtils.toInternalServerErrorStatusLine(requestLine.getProtocolVersion())));
                    throw e;
                }
                try {
                    String statusLine = response.getStatusLine().toString();
                    logger.debug("Response status line: {}", statusLine);

                    outputStream.write(CrlfFormat.format(statusLine));

                    logger.debug("Start writing response headers");
                    for (Header header : response.getAllHeaders()) {
                        if (HttpHeaders.TRANSFER_ENCODING.equals(header.getName())) {

                            // Strip 'chunked' from Transfer-Encoding header's value
                            // since the response is not chunked
                            String nonChunkedTransferEncoding = HttpUtils.stripChunked(header.getValue());
                            if (StringUtils.isNotEmpty(nonChunkedTransferEncoding)) {
                                outputStream.write(
                                        CrlfFormat.format(
                                                HttpUtils.createHttpHeaderAsString(HttpHeaders.TRANSFER_ENCODING,
                                                        nonChunkedTransferEncoding)));
                                logger.debug("Add chunk-striped header response");
                            } else {
                                logger.debug("Remove transfer encoding chunked header response");
                            }
                        } else {
                            String strHeader = header.toString();
                            logger.debug("Write response header: {}", strHeader);
                            outputStream.write(CrlfFormat.format(strHeader));
                        }
                    }

                    // Empty line marking the end
                    // of header's section
                    outputStream.write(CrlfFormat.CRLF.getBytes());

                    // Now write the request body, if any
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        logger.debug("Start writing entity content");
                        entity.writeTo(outputStream);
                        logger.debug("End writing entity content");
                    }

                    // Make sure the entity is fully consumed
                    EntityUtils.consume(entity);
                } finally {
                    LocalIOUtils.close(response);
                }

            } catch (org.apache.http.client.ClientProtocolException e) {
                logger.debug("Error in the HTTP protocol", e);
            } catch (Throwable e) {
                logger.debug("Error on executing HTTP request", e);
            }
            logger.debug("End handling non-connect request {}", requestLine);
        } finally {
            LocalIOUtils.close(httpClient);
        }
    }

}
