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
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * A pseudo-buffered (i.e. buffered under some conditions) entity.
 * This entity is buffered iff is repeatable.
 *
 * @author Eugen Covaci
 */
class PseudoBufferedHttpEntity extends AbstractHttpEntity {

    private final Logger logger = LoggerFactory.getLogger(PseudoBufferedHttpEntity.class);

    private final SessionInputBufferImpl inputBuffer;

    /**
     * The value of Content-Length header.
     */
    private final long contentLength;

    /**
     * Pre-write into this buffer to determine whether the entity
     * should be declared repeatable or not.
     */
    private final byte[] bufferedBytes;

    /**
     * Whether this entity is repeatable or not.<br>
     * If the {@link #contentLength} is bigger than <code>internalBufferLength</code> then is not repeatable.<br>
     * Otherwise is repeatable only if {@link #contentLength} is non-negative or there is no available data.
     */
    private final boolean repeatable;

    public PseudoBufferedHttpEntity(final SessionInputBufferImpl inputBuffer,
                                    final HttpRequest request,
                                    final int internalBufferLength)
            throws IOException {
        Validate.isTrue(internalBufferLength > 0, "internalBufferLength has to be positive");

        this.inputBuffer = inputBuffer;
        this.contentType = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        this.contentEncoding = request.getFirstHeader(HttpHeaders.CONTENT_ENCODING);
        this.contentLength = HttpUtils.getContentLength(request);

        // Set buffer and repeatable
        if (contentLength > internalBufferLength) {
            this.bufferedBytes = new byte[0];
            this.repeatable = false;
        } else {
            logger.debug("Read buffered bytes");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeTo(out, contentLength < 0 ? internalBufferLength : contentLength);
            this.bufferedBytes = out.toByteArray();
            this.repeatable = !(contentLength < 0 && LocalIOUtils.isAvailable(this.inputBuffer));
        }

        logger.debug("bufferedBytes {}", this.bufferedBytes.length);
    }

    private void writeTo(OutputStream out, long maxLength) throws IOException {
        byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
        int length;
        if (maxLength < 0) {
            // consume until EOF
            while (LocalIOUtils.isAvailable(inputBuffer)) {
                length = inputBuffer.read(buffer);
                if (length == -1) {
                    break;
                }
                out.write(buffer, 0, length);
                out.flush();
            }
        } else {
            // consume no more than maxLength
            long remaining = maxLength;
            while (remaining > 0 && LocalIOUtils.isAvailable(inputBuffer)) {
                length = inputBuffer.read(buffer, 0, (int) Math.min(OUTPUT_BUFFER_SIZE, remaining));
                if (length == -1) {
                    break;
                }
                out.write(buffer, 0, length);
                out.flush();
                remaining -= length;
            }
        }
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {

        // Write the initial buffer, regardless of repeatability
        if (bufferedBytes.length > 0) {
            logger.debug("Write initial buffer");
            outputStream.write(bufferedBytes);
            outputStream.flush();
        }

        // Write the remaining bytes when non-repeatable
        if (!repeatable) {
            logger.debug("Write the remaining bytes");
            long remaining = contentLength < 0 ? contentLength : contentLength - bufferedBytes.length;
            writeTo(outputStream, remaining);
        }

    }

    /**
     * It relies on repeatable flag.
     * If <code>true</code>, then the entity is not buffered,
     * otherwise it reads bytes from a socket.
     *
     * @return <code>true</code> if and only if {@link #repeatable} is <code>false</code>.
     */
    @Override
    public boolean isStreaming() {
        return !repeatable;
    }

    /**
     * Whether this entity is repeatable.
     *
     * @return <code>true</code> iff {@link #contentLength} <code>< 0</code>
     * and available bytes less than internal buffer's length
     * or {@link #contentLength} less than internal buffer's length.
     */
    @Override
    public boolean isRepeatable() {
        return repeatable;
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public InputStream getContent() {
        if (isStreaming()) {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return inputBuffer.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return inputBuffer.read(b, off, len);
                }
            };
        } else {
            return new ByteArrayInputStream(this.bufferedBytes);
        }
    }

}