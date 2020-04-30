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

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.kpax.winfoom.util.InputOutputs;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Encapsulates a client's connection with a valid (parsable) HTTP request.
 */
public interface ClientConnection extends Closeable {

    /**
     * @return the input stream of the client's socket
     */
    InputStream getInputStream();

    /**
     * @return the output stream of the client's socket
     */
    OutputStream getOutputStream();

    /**
     * @return the session input buffer used to parse the request into a {@link HttpRequest} instance
     */
    SessionInputBufferImpl getSessionInputBuffer();

    /**
     * @return the HTTP request.
     */
    HttpRequest getHttpRequest();

    /**
     * Write an object to the output stream using CRLF format.
     *
     * @param obj the object
     * @throws IOException
     */
    void write(Object obj) throws IOException;

    /**
     * Write an empty line to the output stream using CRLF format.
     *
     * @throws IOException
     */
    void writeln() throws IOException;

    /**
     * Write a simple response with only the status line with protocol version 1.1, followed by an empty line.
     *
     * @param statusCode the status code.
     * @param e          the message of this error becomes the reasonPhrase from the status line.
     */
    void writeErrorResponse(int statusCode, Exception e);

    /**
     * Write a simple response with only the status line followed by an empty line.
     *
     * @param protocolVersion the request's HTTP version.
     * @param statusCode      the request's status code.
     * @param reasonPhrase    the request's reason code
     */
    void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, String reasonPhrase);

    /**
     * Write a simple response with only the status line, followed by an empty line.
     *
     * @param protocolVersion the request's HTTP version.
     * @param statusCode      the request's status code.
     * @param e               the message of this error becomes the reasonPhrase from the status line.
     */
    void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, Exception e);

    /**
     * Write the response to the output stream as it is.
     *
     * @param httpResponse the HTTP response
     * @throws Exception
     */
    void writeHttpResponse(HttpResponse httpResponse) throws Exception;

    /**
     * Whether the request has been marked as prepared for execution.
     *
     * @return <code>true</code> iff the request has been marked as prepared.
     */
    boolean isRequestPrepared();

    /**
     * Mark the request as prepared for execution.
     */
    void requestPrepared();

    /**
     * Whether there are no more tries.
     *
     * @return <code>true</code> iff no more tries.
     */
    boolean isLastResort();

    /**
     * Signals that there are no more tries.
     */
    void lastResort();

    /**
     * Creates a {@link RequestReadyClientConnection} instance.
     *
     * @param socket the client's socket.
     * @return a new instance if the HTTP request is parsable.
     * @throws IOException
     * @throws HttpException
     */
    static ClientConnection create(Socket socket) throws IOException, HttpException {
        return new RequestReadyClientConnection(socket);
    }


}
