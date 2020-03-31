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
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.kpax.winfoom.util.LocalIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Future;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/31/2020
 */
class Tunnel implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(Tunnel.class);

    private ManagedHttpClientConnection connection;
    private HttpResponse response;
    private ProxyContext proxyContext;

    Tunnel(ManagedHttpClientConnection connection, HttpResponse response, ProxyContext proxyContext) {
        Validate.notNull(connection, "connection cannot be null");
        Validate.notNull(response, "response cannot be null");
        this.connection = connection;
        this.response = response;
        this.proxyContext = proxyContext;
    }

    ManagedHttpClientConnection getConnection() {
        return connection;
    }

    Socket getSocket() {
        return connection.getSocket();
    }

    HttpResponse getResponse() {
        return response;
    }

    StatusLine getStatusLine() {
        return response.getStatusLine();
    }

    void fullDuplex(AsynchronousSocketChannelWrapper localSocketChannel) throws IOException {
        Socket socket = getSocket();
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


    @Override
    public void close() {
        LocalIOUtils.close(connection);
    }
}
