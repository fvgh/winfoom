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
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.io.entity.BufferedHttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.exception.TunnelRefusedHttpException;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.security.auth.login.CredentialException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

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
    private ProxyContext proxyContext;
/*
    @Autowired
    private CustomProxyClient proxyClient;*/

    @Autowired
    private HttpClientBuilder httpClientBuilder;

    private AsynchronousSocketChannelWrapper localSocketChannel;

    SocketHandler bind(final AsynchronousSocketChannel socketChannel) {
        Assert.isNull(localSocketChannel, "Socket already bound!");
        this.localSocketChannel = new AsynchronousSocketChannelWrapper(socketChannel);
        return this;
    }

    void handleRequest() {
        logger.debug("Connection received");
        try {
            // Prepare request parsing (this is the client's request)
            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(LocalIOUtils.DEFAULT_BUFFER_SIZE);
            // inputBuffer.bind(localSocketChannel.getInputStream());

            // Parse the request (all but the message body )
            ClassicHttpRequest request = new DefaultHttpRequestParser().parse(inputBuffer, localSocketChannel.getInputStream());

            System.out.println(request);

            BaseRequestLine requestLine = new BaseRequestLine(request);

            logger.debug("Start processing request {}", requestLine);
            if (HttpUtils.HTTP_CONNECT.equalsIgnoreCase(request.getMethod())) {
                handleConnect(requestLine);
            } else {
                handleNonConnectRequest(request, inputBuffer);
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
     * Creates a tunnel through proxy, then let the client and the remote proxy
     * communicate via the local socket channel instance.
     *
     * @param requestLine The first line of the request.
     * @throws HttpException
     * @throws IOException
     */
    private void handleConnect(final BaseRequestLine requestLine) throws HttpException, IOException {
        logger.debug("Handle proxy connect request");
        Pair<String, Integer> hostPort = HttpUtils.parseConnectUri(requestLine.getUri().toString());
        HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
        HttpHost target = new HttpHost(hostPort.getLeft(), hostPort.getRight());

        ProxyClient proxyClient = new ProxyClient();
        try (Tunnel tunnel = proxyClient.tunnel(proxy, target)) {
            handleConnectResponse(tunnel);
        } catch (TunnelRefusedHttpException tre) {
            logger.debug("The tunnel request was rejected by the proxy host", tre);
            writeHttpResponse(tre.getResponse());
        }

    }

    /**
     * Handles the tunnel's response.<br>
     * <b>It should not throw any exception!</b>
     *
     * @param tunnel The tunnel's instance
     */
    private void handleConnectResponse(final Tunnel tunnel) {
        try {
            logger.debug("Write status line");
            localSocketChannel.write(tunnel.getStatusLine());

            logger.debug("Write empty line");
            localSocketChannel.writeln();

            logger.debug("Start full duplex communication");
            fullDuplex(tunnel, localSocketChannel);
        } catch (Exception e) {
            logger.debug("Error on handling CONNECT response", e);
        }
    }

    void fullDuplex(Tunnel tunnel, AsynchronousSocketChannelWrapper localSocketChannel) throws IOException {
        Socket socket = tunnel.getSocket();
        final OutputStream socketOutputStream = socket.getOutputStream();
        Future<?> localToSocket = proxyContext.executeAsync(
                () -> LocalIOUtils.copyQuietly(localSocketChannel.getInputStream(),
                        socketOutputStream));
        LocalIOUtils.copyQuietly(socket.getInputStream(), localSocketChannel.getOutputStream());
        if (!localToSocket.isDone()) {
            try {
                // Wait for async copy to finish
                localToSocket.get();
            } catch (Exception e) {
                logger.debug("Error on writing to socket", e);
            }
        }
    }

    /**
     * Writes the HTTP response as it is.
     *
     * @param httpResponse The HTTP response.
     */
    private void writeHttpResponse(final ClassicHttpResponse httpResponse) {
        try {
            StatusLine statusLine = new StatusLine(httpResponse);
            logger.debug("Write statusLine {}", statusLine);
            localSocketChannel.write(statusLine);

            logger.debug("Write headers");
            for (Header header : httpResponse.getHeaders()) {
                localSocketChannel.write(header);
            }

            // Empty line between headers and the body
            localSocketChannel.writeln();

            HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                logger.debug("Write entity content");
                entity.writeTo(localSocketChannel.getOutputStream());
            }
            EntityUtils.consume(entity);
        } catch (Exception e) {
            logger.debug("Error on writing response", e);
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
    private void handleNonConnectRequest(final ClassicHttpRequest request, final SessionInputBufferImpl inputBuffer)
            throws IOException, URISyntaxException, CredentialException {
        BaseRequestLine requestLine = new BaseRequestLine(request);
        logger.debug("Handle non-connect request {}", requestLine);

        // Set the streaming entity
        if (request.getEntity() != null) {
            logger.debug("Create and set PseudoBufferedHttpEntity instance");
            request.setEntity(new BufferedHttpEntity(request.getEntity()));
        } else {
            logger.debug("No enclosing entity");
        }

        // Remove banned headers
        List<String> bannedHeaders = request.getEntity() != null ?
                ENTITY_BANNED_HEADERS : DEFAULT_BANNED_HEADERS;
        for (Header header : request.getHeaders()) {
            if (bannedHeaders.contains(header.getName())) {
                request.removeHeader(header);
                logger.debug("Request header {} removed", header);
            } else {
                logger.debug("Allow request header {}", header);
            }
        }

        try (CloseableHttpClient httpClient = httpClientBuilder.build()) {

            // Extract URI
            URI uri = HttpUtils.parseUri(requestLine.getUri().toString());
            HttpHost target = new HttpHost( uri.getScheme(), uri.getHost(), uri.getPort());

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                // Execute the request
                handleNonConnectResponse(response);
            }
        }
    }

    /**
     * Handles the Http response for non-CONNECT requests.<br>
     * <b>It should not throw any exception!</b>
     *
     * @param response The Http response.
     */
    private void handleNonConnectResponse(final CloseableHttpResponse response) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Write status line: {}", new StatusLine(response));
            }
            localSocketChannel.write(new StatusLine(response));

            logger.debug("Write response headers");
            for (Header header : response.getHeaders()) {
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
        } catch (Exception e) {
            logger.debug("Error on handling non CONNECT response", e);
        }
    }

/*    private class SessionInputStream extends InputStream {

        private SessionInputBufferImpl inputBuffer;

        public SessionInputStream(SessionInputBufferImpl inputBuffer) {
            this.inputBuffer = inputBuffer;
        }

        @Override
        public int read() throws IOException {
            throw new NotImplementedException("Do not use it");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return this.inputBuffer.read(b, off, len);
        }
    }*/

}
