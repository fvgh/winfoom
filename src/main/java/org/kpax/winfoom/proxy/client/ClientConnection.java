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

package org.kpax.winfoom.proxy.client;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.kpax.winfoom.util.LocalIOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public interface ClientConnection {

    InputStream getInputStream();

    OutputStream getOutputStream();

    SessionInputBufferImpl getSessionInputBuffer();

    HttpRequest getHttpRequest();

    void write(Object obj) throws IOException;

    void writeln() throws IOException;

    void writeErrorResponse(int statusCode, Exception e);

    void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, String reasonPhrase);

    void writeErrorResponse(ProtocolVersion protocolVersion, int statusCode, Exception e);

    void writeHttpResponse(HttpResponse httpResponse) throws Exception;

    Set<Flag> flags();

    default boolean flag(Flag flag) {
        if (flag != null) {
            return flags().add(flag);
        }
        return false;
    }

    default boolean isFlagged(Flag flag) {
        if (flag != null) {
            return flags().contains(flag);
        }
        return false;
    }

    static ClientConnection create(Socket socket) throws IOException, HttpException {
        InputStream inputStream = socket.getInputStream();
        SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(
                new HttpTransportMetricsImpl(),
                LocalIOUtils.DEFAULT_BUFFER_SIZE,
                LocalIOUtils.DEFAULT_BUFFER_SIZE,
                MessageConstraints.DEFAULT,
                StandardCharsets.UTF_8.newDecoder());
        sessionInputBuffer.bind(inputStream);
        HttpRequest httpRequest = new DefaultHttpRequestParser(sessionInputBuffer).parse();
        return new ClientConnectionImpl(inputStream, socket.getOutputStream(), sessionInputBuffer, httpRequest);
    }

    enum Flag {
        REQUEST_PREPARED,
        LAST_RESORT
    }
}
