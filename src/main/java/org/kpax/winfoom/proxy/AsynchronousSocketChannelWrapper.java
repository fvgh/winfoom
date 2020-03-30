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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.Validate;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpStatus;
import org.kpax.winfoom.util.CrlfFormat;
import org.kpax.winfoom.util.HttpUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;

/**
 * A helper class that wraps an {@link AsynchronousSocketChannel}.
 *
 * @author Eugen Covaci
 */
class AsynchronousSocketChannelWrapper implements Closeable {

    private final AsynchronousSocketChannel socketChannel;

    private final InputStream inputStream;

    private final OutputStream outputStream;

    public AsynchronousSocketChannelWrapper(AsynchronousSocketChannel socketChannel) {
        Validate.notNull(socketChannel, "socketChannel cannot be null");
        this.socketChannel = socketChannel;
        inputStream = new SocketChannelInputStream();
        outputStream = new SocketChannelOutputStream();
    }

    public AsynchronousSocketChannel getSocketChannel() {
        return socketChannel;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void write(Object obj) throws IOException {
        outputStream.write(CrlfFormat.format(obj));
    }

    public void writeln(Object obj) throws IOException {
        write(obj);
        writeln();
    }

    public void writeln() throws IOException {
        outputStream.write(CrlfFormat.CRLF.getBytes());
    }

    public void writelnError(Exception e) throws IOException {
        if (HttpUtils.isWritableException(e.getClass())) {
            if (HttpUtils.isClientException(e.getClass())) {
                writeln(HttpUtils.toStatusLine(HttpStatus.SC_BAD_REQUEST, e.getMessage()));
            } else if (HttpUtils.isGatewayException(e.getClass())) {
                writeln(HttpUtils.toStatusLine(HttpStatus.SC_BAD_GATEWAY, "Cannot connect to the remote proxy server"));
            } else {
                writeln(HttpUtils.toStatusLine(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        }
    }

    public void writelnError(int statusCode, Exception e) throws IOException {
        writeln(HttpUtils.toStatusLine(statusCode, e.getMessage()));
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
    }

    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    private class SocketChannelInputStream extends InputStream {

        @Override
        public int read() {
            throw new NotImplementedException("Do not use it");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            try {
                return socketChannel.read(buffer).get();
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());
            } catch (Exception e) {
                throw new IOException(e);
            }

        }

    }

    private class SocketChannelOutputStream extends OutputStream {

        @Override
        public void write(int b) {
            throw new NotImplementedException("Do not use it");
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            try {
                socketChannel.write(buffer).get();
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

    }

}
