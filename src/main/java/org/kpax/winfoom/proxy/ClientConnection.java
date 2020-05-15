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
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.InputOutputs;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * It encapsulates a client's connection.<br>
 * It provides various methods for exchanging data with the client,
 * also some information about the state of the request's processing.<br>
 * <b>Note:</b> This class doesn't have the responsibility to close the underlying socket.
 *
 * @author Eugen Covaci
 */
final class ClientConnection implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(ClientConnection.class);

    /**
     * The list of {@link AutoCloseable}s to be closed when this instance's {@link #close()} method is called.
     */
    private final Set<AutoCloseable> autoCloseables = new HashSet<>();

    /**
     * The underlying socket.
     */
    private final Socket socket;

    /**
     * The socket's input stream.
     */
    private final InputStream inputStream;

    /**
     * The socket's output stream.
     */
    private final OutputStream outputStream;

    /**
     * Used for request parsing also by the {@link org.kpax.winfoom.proxy.RepeatableHttpEntity} class.
     */
    private final SessionInputBufferImpl sessionInputBuffer;

    /**
     * The parsed {@link HttpRequest}.
     */
    private final HttpRequest httpRequest;

    /**
     * The request line. We keep a reference here to avoid multiple calling to {@link HttpRequest#getRequestLine()}
     */
    private final RequestLine requestLine;

    private final URI requestUri;

    /**
     * Whether the request is prepared (it means the request headers are set, also the request entity - if any)<br>
     * Only makes sense for non-CONNECT HTTP requests.
     */
    private boolean requestPrepared;

    /**
     * Constructor.<br>
     * Has the responsibility of parsing the request.
     *
     * @param socket the underlying socket.
     * @throws IOException
     * @throws HttpException
     */
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
        try {
            this.requestUri = HttpUtils.parseRequestUri(this.requestLine);
        } catch (URISyntaxException e) {
            throw new HttpException("Invalid request uri", e);
        }
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

    URI getRequestUri() {
        return requestUri;
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
        return requestPrepared;
    }

    /**
     * Mark the request as prepared for execution.
     */
    void requestPrepared() {
        this.requestPrepared = true;
    }

    /**
     * @return {@code true} iff the underlying socket is closed.
     */
    boolean isClosed() {
        return socket.isClosed();
    }

    /**
     * @return the request's line
     */
    RequestLine getRequestLine() {
        return requestLine;
    }

    /**
     * Register an {@link AutoCloseable} for later closing.
     *
     * @param autoCloseable the {@link AutoCloseable} to be closed.
     * @return {@code true} if the specified element isn't already registered
     */
    boolean registerAutoCloseable(AutoCloseable autoCloseable) {
        return autoCloseables.add(autoCloseable);
    }


    @Override
    public void close() {
        autoCloseables.forEach(InputOutputs::close);
    }
}
