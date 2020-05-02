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

import org.apache.commons.lang3.Validate;
import org.apache.http.*;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.exception.InvalidPacFileException;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.InputOutputs;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugen Covaci
 */
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
public class ClientConnection implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(ClientConnection.class);

    private final Socket socket;

    private final InputStream inputStream;

    private final OutputStream outputStream;

    private final SessionInputBufferImpl sessionInputBuffer;

    private final HttpRequest httpRequest;

    private final RequestLine requestLine;

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private ProxyAutoConfig proxyAutoconfig;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    @Autowired
    private ClientProcessorSelector clientProcessorSelector;

    private boolean requestPrepared;

    private boolean lastResort;

    ClientConnection(Socket socket) throws IOException, HttpException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.sessionInputBuffer = new SessionInputBufferImpl(
                new HttpTransportMetricsImpl(),
                InputOutputs.DEFAULT_BUFFER_SIZE,
                InputOutputs.DEFAULT_BUFFER_SIZE,
                MessageConstraints.DEFAULT,
                StandardCharsets.UTF_8.newDecoder());
        this.sessionInputBuffer.bind(this.inputStream);
        this.httpRequest = new DefaultHttpRequestParser(this.sessionInputBuffer).parse();
        this.requestLine = httpRequest.getRequestLine();
    }

    /**
     * @return the input stream of the client's socket
     */
    InputStream getInputStream() {
        return inputStream;
    }

    /**
     * @return the output stream of the client's socket
     */
    OutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * @return the session input buffer used to parse the request into a {@link HttpRequest} instance
     */
    SessionInputBufferImpl getSessionInputBuffer() {
        return sessionInputBuffer;
    }

    /**
     * @return the HTTP request.
     */
    HttpRequest getHttpRequest() {
        return httpRequest;
    }

    /**
     * Write an object to the output stream using CRLF format.
     *
     * @param obj the object
     * @throws IOException
     */
    void write(Object obj) throws IOException {
        outputStream.write(ObjectFormat.toCrlf(obj, StandardCharsets.UTF_8));
    }

    /**
     * Write an empty line to the output stream using CRLF format.
     *
     * @throws IOException
     */
    void writeln() throws IOException {
        outputStream.write(ObjectFormat.CRLF.getBytes());
    }

    /**
     * Write a simple response with only the status line with protocol version 1.1, followed by an empty line.
     *
     * @param statusCode the status code.
     * @param e          the message of this error becomes the reasonPhrase from the status line.
     */
    void writeErrorResponse(int statusCode, Exception e) {
        writeErrorResponse(HttpVersion.HTTP_1_1, statusCode, e);
    }

    /**
     * Write a simple response with only the status line followed by an empty line.
     *
     * @param protocolVersion the request's HTTP version.
     * @param statusCode      the request's status code.
     * @param reasonPhrase    the request's reason code
     */
    void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, String reasonPhrase) {
        try {
            write(HttpUtils.toStatusLine(protocolVersion, statusCode, reasonPhrase));
            writeln();
        } catch (Exception ex) {
            logger.debug("Error on writing error response", ex);
        }
    }

    /**
     * Write a simple response with only the status line, followed by an empty line.
     *
     * @param protocolVersion the request's HTTP version.
     * @param statusCode      the request's status code.
     * @param e               the message of this error becomes the reasonPhrase from the status line.
     */
    void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, Exception e) {
        Validate.notNull(e, "Exception cannot be null");
        writeErrorResponse(protocolVersion, statusCode, e.getMessage());
    }

    /**
     * Write the response to the output stream as it is.
     *
     * @param httpResponse the HTTP response
     * @throws Exception
     */
    void writeHttpResponse(final HttpResponse httpResponse) throws Exception {
        StatusLine statusLine = httpResponse.getStatusLine();
        logger.debug("Write statusLine {}", statusLine);
        write(statusLine);

        logger.debug("Write headers");
        for (Header header : httpResponse.getAllHeaders()) {
            write(header);
        }

        // Empty line between headers and the body
        writeln();

        HttpEntity entity = httpResponse.getEntity();
        if (entity != null) {
            logger.debug("Write entity content");
            entity.writeTo(outputStream);
        }
        EntityUtils.consume(entity);
    }

    /**
     * Whether the request has been marked as prepared for execution.
     *
     * @return <code>true</code> iff the request has been marked as prepared.
     */
    boolean isRequestPrepared() {
        return this.requestPrepared;
    }

    /**
     * Mark the request as prepared for execution.
     */
    void requestPrepared() {
        this.requestPrepared = true;
    }

    /**
     * Whether there are no more tries.
     *
     * @return <code>true</code> iff no more tries.
     */
    boolean isLastResort() {
        return this.lastResort;
    }

    boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() {
        InputOutputs.close(socket);
    }

    /**
     * @return the request's line
     */
    RequestLine getRequestLine() {
        return requestLine;
    }

    /**
     * Process the client connection with each available proxy.<br>
     * Un un-responding proxy is blacklisted only if it is not the last
     * one available.
     */
    void handleRequest() {
        logger.debug("Handle request: {}", requestLine);

        try {
            List<ProxyInfo> proxyInfoList;
            if (proxyConfig.isAutoConfig()) {
                HttpHost host = HttpHost.create(requestLine.getUri());
                proxyInfoList = proxyAutoconfig.findProxyForURL(new URI(host.toURI()));
            } else {

                // Manual proxy case
                HttpHost proxyHost = proxyConfig.getProxyType().isDirect() ? null :
                        new HttpHost(proxyConfig.getProxyHost(), proxyConfig.getProxyPort());

                proxyInfoList = Collections.singletonList(new ProxyInfo(proxyConfig.getProxyType(), proxyHost));
            }

            logger.debug("proxyInfoList {}", proxyInfoList);

            ClientConnectionProcessor connectionProcessor;
            for (Iterator<ProxyInfo> itr = proxyInfoList.iterator(); itr.hasNext(); ) {
                ProxyInfo proxyInfo = itr.next();

                if (itr.hasNext()) {
                    if (proxyBlacklist.checkBlacklist(proxyInfo)) {
                        logger.debug("Blacklisted proxy {} - skip it", proxyInfo);
                        continue;
                    }
                } else {
                    this.lastResort = true;
                }

                connectionProcessor = clientProcessorSelector.selectClientProcessor(requestLine, proxyInfo);

                try {
                    logger.debug("Process connection with proxy: {}", proxyInfo);
                    connectionProcessor.process(this, proxyInfo);

                    // Success, break the iteration
                    break;
                } catch (ConnectException e) {
                    logger.debug("Connection error", e);
                    if (itr.hasNext()) {
                        logger.debug("Failed to process connection with proxy: {}, retry with the next one", proxyInfo);
                        proxyBlacklist.blacklist(proxyInfo);
                    } else {
                        logger.debug("Failed to process connection with proxy: {}, send the error response", proxyInfo);

                        // Cannot connect to the remote proxy
                        writeErrorResponse(HttpStatus.SC_BAD_GATEWAY, e);
                    }
                } catch (Exception e) {

                    if (HttpUtils.isConnectionAborted(e)) {
                        logger.debug("Client's connection aborted", e);
                    } else {
                        logger.debug("Generic error, send the error response", e);

                        // Any other error, including client errors
                        writeErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
                    }

                    break;
                }
            }
        } catch (InvalidPacFileException e) {
            writeErrorResponse(requestLine.getProtocolVersion(),
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Invalid Proxy Auto Config file");
            logger.debug("Error on calling PAC function", e);
        } catch (
                URISyntaxException e) {
            writeErrorResponse(requestLine.getProtocolVersion(),
                    HttpStatus.SC_BAD_REQUEST,
                    "Invalid request URI");
            logger.debug("Error on calling PAC function", e);
        } finally {
            InputOutputs.close(socket);
        }
        logger.debug("Done handling request: {}", requestLine);

    }

}
