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

import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.RequestLine;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.util.LocalIOUtils;
import org.kpax.winfoom.util.ObjectFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.net.ConnectException;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * This class handles the communication client <-> proxy facade <-> remote proxy. <br>
 *
 * @author Eugen Covaci
 */
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class SocketHandler {

    private final Logger logger = LoggerFactory.getLogger(SocketHandler.class);

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private RequestHandlerFactory requestHandlerFactory;

    private AsynchronousSocketChannelWrapper socketChannelWrapper;

    SocketHandler bind(final AsynchronousSocketChannel socketChannel) {
        Assert.isNull(socketChannelWrapper, "Socket already bound!");
        this.socketChannelWrapper = new AsynchronousSocketChannelWrapper(socketChannel, systemConfig.getSocketChannelTimeout());
        return this;
    }

    void handleConnection() {
        logger.debug("Connection received");
        try {
            // Prepare request parsing (this is the client's request)
            SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(
                    new HttpTransportMetricsImpl(),
                    LocalIOUtils.DEFAULT_BUFFER_SIZE,
                    LocalIOUtils.DEFAULT_BUFFER_SIZE,
                    MessageConstraints.DEFAULT,
                    ObjectFormat.UTF_8.newDecoder());
            sessionInputBuffer.bind(socketChannelWrapper.getInputStream());

            // Parse the request (all but the message body )
            HttpRequest request = new DefaultHttpRequestParser(sessionInputBuffer).parse();
            RequestLine requestLine = request.getRequestLine();

            logger.debug("Start processing request {}", requestLine);
            requestHandlerFactory.createRequestHandler(requestLine).handleRequest(request,
                    sessionInputBuffer,
                    socketChannelWrapper);
            logger.debug("End processing request {}", requestLine);

        } catch (ConnectException e) {

            // Cannot connect to the remote proxy
            socketChannelWrapper.writelnError(HttpStatus.SC_BAD_GATEWAY, e);
            logger.debug("Connection error", e);
        } catch (Exception e) {

            // Any other error, including client errors
            socketChannelWrapper.writelnError(HttpStatus.SC_INTERNAL_SERVER_ERROR, e);
            logger.debug("Generic error", e);
        } finally {
            LocalIOUtils.close(socketChannelWrapper);
        }

    }

}
