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
import java.net.Socket;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/31/2020
 */
class Tunnel implements Closeable {

    private ManagedHttpClientConnection connection;
    private HttpResponse response;

    Tunnel(ManagedHttpClientConnection connection, HttpResponse response) {
        Validate.notNull(connection, "connection cannot be null");
        Validate.notNull(response, "response cannot be null");
        this.connection = connection;
        this.response = response;
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

    @Override
    public void close() {
        LocalIOUtils.close(connection);
    }
}
