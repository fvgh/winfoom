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
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.util.EntityUtils;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * A helper class that wraps an {@link AsynchronousSocketChannel}.
 *
 * @author Eugen Covaci
 */
class SocketWrapper implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(SocketWrapper.class);

    private final Socket socket;

    private final InputStream inputStream;

    private final OutputStream outputStream;

    private final SessionInputBufferImpl sessionInputBuffer;


    SocketWrapper(Socket socket) throws IOException {
        Validate.notNull(socket, "socket cannot be null");
        this.socket = socket;
        this.inputStream = this.socket.getInputStream();
        this.outputStream = this.socket.getOutputStream();
        this.sessionInputBuffer = createSessionInputBuffer();
    }

    Socket getSocketChannel() {
        return socket;
    }

    InputStream getInputStream() {
        return inputStream;
    }

    OutputStream getOutputStream() {
        return outputStream;
    }

    SessionInputBufferImpl getSessionInputBuffer() {
        return sessionInputBuffer;
    }

    private SessionInputBufferImpl createSessionInputBuffer() {
        SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(
                new HttpTransportMetricsImpl(),
                LocalIOUtils.DEFAULT_BUFFER_SIZE,
                LocalIOUtils.DEFAULT_BUFFER_SIZE,
                MessageConstraints.DEFAULT,
                StandardCharsets.UTF_8.newDecoder());
        sessionInputBuffer.bind(this.inputStream);
        return sessionInputBuffer;
    }

    void write(Object obj) throws IOException {
        outputStream.write(ObjectFormat.toCrlf(obj, StandardCharsets.UTF_8));
    }

    void writeln() throws IOException {
        outputStream.write(ObjectFormat.CRLF.getBytes());
    }

    void writeErrorResponse(int statusCode, Exception e) {
        writeErrorResponse(HttpVersion.HTTP_1_1, statusCode, e);
    }

    void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, Exception e) {
        Validate.notNull(e, "Exception cannot be null");
        try {
            write(HttpUtils.toStatusLine(protocolVersion, statusCode, e.getMessage()));
            writeln();
        } catch (Exception ex) {
            logger.debug("Error on writing response error", ex);
        }
    }

    /**
     * Writes the HTTP response as it is.
     *
     * @param httpResponse The HTTP response.
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

    @Override
    public void close() throws IOException {
        socket.close();
    }


}
