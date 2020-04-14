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

import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/6/2020
 */
class RepeatableHttpEntity extends AbstractHttpEntity implements Closeable {

    private final SessionInputBufferImpl inputBuffer;

    private final Path tempDirectory;

    /**
     * The value of Content-Length header.
     */
    private final long contentLength;

    /**
     * Write into this buffer when contentLength < maximum buffered.
     */
    private byte[] bufferedBytes;

    private Path tempFilepath;

    private boolean firstTry = true;

    RepeatableHttpEntity(final SessionInputBufferImpl inputBuffer,
                         final Path tempDirectory,
                         final HttpRequest request,
                         final int internalBufferLength) throws IOException {
        this.inputBuffer = inputBuffer;
        this.tempDirectory = tempDirectory;
        this.contentType = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        this.contentEncoding = request.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        this.contentLength = HttpUtils.getContentLength(request);

        if (this.contentLength > 0 && this.contentLength <= internalBufferLength) {
            writeToBuffer();
        }
    }

    private void writeToBuffer() throws IOException {
        int length;
        byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long remaining = contentLength;
        while (remaining > 0 && LocalIOUtils.isAvailable(inputBuffer)) {
            length = inputBuffer.read(buffer, 0, (int) Math.min(OUTPUT_BUFFER_SIZE, remaining));
            if (length == -1) {
                break;
            }
            out.write(buffer, 0, length);
            remaining -= length;
        }
        out.flush();
        bufferedBytes = out.toByteArray();
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        if (bufferedBytes != null) {
            return new ByteArrayInputStream(bufferedBytes);
        } else if (contentLength == 0) {
            return new ByteArrayInputStream(new byte[0]);
        } else {
            if (firstTry) {
                return new InputStream() {
                    @Override
                    public int read() {
                        throw new UnsupportedOperationException("Do not use it");
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        return inputBuffer.read(b, off, len);
                    }
                };
            } else {
                return Files.newInputStream(tempFilepath);
            }
        }
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        if (bufferedBytes != null) {
            outStream.write(bufferedBytes);
            outStream.flush();
        } else if (contentLength != 0) {
            if (firstTry) {
                tempFilepath = tempDirectory.resolve(LocalIOUtils.generateCacheFilename());
                try (AsynchronousFileChannel tempFileChannel
                             = AsynchronousFileChannel.open(tempFilepath,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE)) {
                    byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
                    long position = 0;

                    if (contentLength < 0) {
                        if (isChunked()) {
                            ChunkedInputStream chunkedInputStream = new ChunkedInputStream(inputBuffer);

                            int length;
                            while ((length = chunkedInputStream.read(buffer)) > 0) {

                                outStream.write(buffer, 0, length);
                                outStream.flush();

                                // Write to file
                                tempFileChannel.write(ByteBuffer.wrap(buffer, 0, length), position);
                                position += length;
                            }
                        } else {

                            // consume until EOF
                            int length;
                            while (LocalIOUtils.isAvailable(inputBuffer)) {
                                length = inputBuffer.read(buffer);
                                if (length == -1) {
                                    break;
                                }
                                outStream.write(buffer, 0, length);
                                outStream.flush();

                                // Write to file
                                tempFileChannel.write(ByteBuffer.wrap(buffer, 0, length), position);
                                position += length;
                            }
                        }

                    } else {
                        int length;
                        long remaining = contentLength;

                        // consume no more than maxLength
                        while (remaining > 0 && LocalIOUtils.isAvailable(inputBuffer)) {
                            length = inputBuffer.read(buffer, 0, (int) Math.min(OUTPUT_BUFFER_SIZE, remaining));
                            if (length == -1) {
                                break;
                            }
                            outStream.write(buffer, 0, length);
                            outStream.flush();
                            remaining -= length;

                            // Write to temp file
                            tempFileChannel.write(ByteBuffer.wrap(buffer, 0, length), position);
                            position += length;
                        }
                    }
                }
                firstTry = false;
            } else {

                //read from file
                try (InputStream inputStream = Files.newInputStream(tempFilepath)) {
                    inputStream.transferTo(outStream);
                    outStream.flush();
                }
            }
        }
    }

    @Override
    public boolean isStreaming() {
        return bufferedBytes == null && firstTry;
    }

    @Override
    public void close() throws IOException {

        // Delete the temp file if exists
        if (tempFilepath != null) {
            Files.deleteIfExists(tempFilepath);
        }
    }
}
