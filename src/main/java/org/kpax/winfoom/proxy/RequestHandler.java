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
import org.apache.http.impl.io.SessionInputBufferImpl;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/13/2020
 */
public interface RequestHandler {

    /**
     * Process the client's HTTP request.
     *
     * @param request              The request.
     * @param sessionInputBuffer   The session input buffer instance.
     * @param socketChannelWrapper The {@link java.nio.channels.AsynchronousSocketChannel} wrapper instance.
     * @throws IOException
     * @throws URISyntaxException
     * @throws HttpException
     */
    void handleRequest(HttpRequest request,
                       SessionInputBufferImpl sessionInputBuffer,
                       AsynchronousSocketChannelWrapper socketChannelWrapper)
            throws IOException, URISyntaxException, HttpException;

}
