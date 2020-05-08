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

import org.apache.commons.lang3.Validate;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author Eugen Covaci
 */
public final class InputOutputs {

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    private static final Logger logger = LoggerFactory.getLogger(InputOutputs.class);

    private InputOutputs() {
    }

    /**
     * Check for available data.
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
     * Close an <code>AutoCloseable</code>, debug the possible error.
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


    /**
     * Transfer bytes between two sources.
     *
     * @param executorService    The executor service for async support.
     * @param firstInputSource   The input of the first source.
     * @param firstOutputSource  The output of the first source.
     * @param secondInputSource  The input of the second source.
     * @param secondOutputSource The output of the second source.
     */
    public static void duplex(ExecutorService executorService,
                              InputStream firstInputSource, OutputStream firstOutputSource,
                              InputStream secondInputSource, OutputStream secondOutputSource) {

        logger.debug("Start full duplex communication");
        Future<?> secondToFirst = executorService.submit(
                () -> secondInputSource.transferTo(firstOutputSource));
        try {
            firstInputSource.transferTo(secondOutputSource);
            if (!secondToFirst.isDone()) {

                // Wait for the async transfer to finish
                try {
                    secondToFirst.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof SocketTimeoutException) {
                        logger.debug("Second to first transfer cancelled due to timeout");
                    } else {
                        logger.debug("Error on executing second to first transfer", e.getCause());
                    }
                } catch (InterruptedException e) {
                    logger.debug("Transfer from second to first interrupted", e);
                } catch (CancellationException e) {
                    logger.debug("Transfer from second to first cancelled", e);
                }
            }
        } catch (Exception e) {
            secondToFirst.cancel(true);
            if (e instanceof SocketTimeoutException) {
                logger.debug("Second to first transfer cancelled due to timeout");
            } else {
                logger.debug("Error on executing second to first transfer", e);
            }
        }
        logger.debug("End full duplex communication");
    }

    public static boolean isIncluded(Properties who, Properties where) {
        Validate.notNull(who, "who cannot be null");
        Validate.notNull(where, "where cannot be null");
        for (String key : who.stringPropertyNames()) {
            if (where.getProperty(key) == null) {
                return false;
            }
        }
        return true;
    }


    public static boolean deleteFile(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteFile(f);
            }
        }
        return file.delete();
    }

    public static boolean emptyDirectory(File directory) {
        Validate.isTrue(directory.isDirectory());
        File[] files = directory.listFiles();
        for (File file : Objects.requireNonNull(files)) {
            deleteFile(file);
        }
        return files.length == 0;
    }

}
