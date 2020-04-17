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

package org.kpax.winfoom.util;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.Validate;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.io.SessionInputBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author Eugen Covaci
 */
public final class LocalIOUtils {

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private static final Logger logger = LoggerFactory.getLogger(LocalIOUtils.class);

    private LocalIOUtils() {
    }

    /**
     * It checks for available data.
     *
     * @param inputBuffer The input buffer.
     * @return <code>false</code> iff EOF has been reached.
     */
    public static boolean isAvailable(SessionInputBufferImpl inputBuffer) {
        try {
            return inputBuffer.hasBufferedData() || inputBuffer.fillBuffer() > -1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Close all <code>closeables</code>.
     *
     * @param closeable The {@link AutoCloseable} instance.
     */
    public static void close(AutoCloseable closeable) {
        if (closeable != null) {
            logger.debug("Close {}", closeable.getClass());
            try {
                closeable.close();
            } catch (Exception e) {
                logger.debug("Fail to close: " + closeable.getClass().getName(), e);
            }
        }

    }

    public static String generateCacheFilename() {
        return new StringBuffer()
                .append(System.nanoTime())
                .append("-")
                .append((int) (Math.random() * 100)).toString();
    }


    public static void duplex(ExecutorService executorService,
                              InputStream firstInputSource, OutputStream firstOutputSource,
                              InputStream secondInputSource, OutputStream secondOutputSource) throws IOException {
        logger.debug("Start full duplex communication");
        Future<?> localToSocket = executorService.submit(
                () -> secondInputSource.transferTo(firstOutputSource));
        try {
            firstInputSource.transferTo(secondOutputSource);
            if (!localToSocket.isDone()) {

                // Wait for async copy to finish
                try {
                    localToSocket.get();
                } catch (ExecutionException e) {
                    logger.debug("Error on writing bytes", e.getCause());
                } catch (Exception e) {
                    logger.debug("Failed to write bytes", e);
                }
            }
        } catch (Exception e) {
            localToSocket.cancel(true);
            logger.debug("Error on first to second transfer", e);
        }
        logger.debug("End full duplex communication");
    }

    public static class SessionInputStream extends InputStream {

        private SessionInputBuffer sessionInputBuffer;

        public SessionInputStream(SessionInputBuffer sessionInputBuffer) {
            Validate.notNull(sessionInputBuffer, "sessionInputBuffer cannot be null");
            this.sessionInputBuffer = sessionInputBuffer;
        }

        @Override
        public int read() throws IOException {
            throw new NotImplementedException("Do not use it");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return sessionInputBuffer.read(b, off, len);
        }
    }

}
