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
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.LocalIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/6/2020
 */
public class RepetableHttpEntity extends AbstractHttpEntity implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(RepetableHttpEntity.class);

    private final SessionInputBufferImpl inputBuffer;

    private final Path cacheDirectory;

    /**
     * The value of Content-Length header.
     */
    private final long contentLength;

    /**
     * Write into this buffer when contentLength < maximum buffered.
     */
    private byte[] bufferedBytes;

    private Path filepath;

    private boolean firstTry = true;

    public RepetableHttpEntity(final SessionInputBufferImpl inputBuffer,
                               final Path cacheDirectory,
                               final HttpRequest request,
                               final int internalBufferLength) throws IOException {
        this.inputBuffer = inputBuffer;
        this.cacheDirectory = cacheDirectory;
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
        return null;
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        if (bufferedBytes != null) {
            outStream.write(bufferedBytes);
            outStream.flush();
        } else if (contentLength != 0) {
            if (firstTry) {
                filepath = cacheDirectory.resolve(LocalIOUtils.generateCacheFilename());
                try (BufferedOutputStream bufferedOutputStream =  new BufferedOutputStream(
                        Files.newOutputStream(filepath))) {
                    byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
                    int length;
                    if (contentLength < 0) {
                        // consume until EOF
                        while (LocalIOUtils.isAvailable(inputBuffer)) {
                            length = inputBuffer.read(buffer);
                            if (length == -1) {
                                break;
                            }
                            outStream.write(buffer, 0, length);
                            outStream.flush();

                            // Write to file
                            bufferedOutputStream.write(buffer, 0, length);
                        }
                    } else {
                        // consume no more than maxLength
                        long remaining = contentLength;
                        while (remaining > 0 && LocalIOUtils.isAvailable(inputBuffer)) {
                            length = inputBuffer.read(buffer, 0, (int) Math.min(OUTPUT_BUFFER_SIZE, remaining));
                            if (length == -1) {
                                break;
                            }
                            outStream.write(buffer, 0, length);
                            outStream.flush();
                            remaining -= length;

                            // Write to file
                            bufferedOutputStream.write(buffer, 0, length);
                        }
                    }
                }
                firstTry = false;
            } else {

                //read from file
                InputStream inputStream = Files.newInputStream(filepath);
                inputStream.transferTo(outStream);
            }
        }
    }

    @Override
    public boolean isStreaming() {
        return bufferedBytes == null && firstTry;
    }

    @Override
    public void close() throws IOException {
        // Delete the cache file
        if (filepath != null) {
            Files.deleteIfExists(filepath);
        }
    }
}
/*
    String filePath = "D:\\tmp\\async_file_write.txt";
    Path file = Paths.get(filePath);
    try(AsynchronousFileChannel asyncFile = AsynchronousFileChannel.open(file,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE)) {

        asyncFile.write(ByteBuffer.wrap("Some text to be written".getBytes()), 0);
        } catch (IOException e) {
        e.printStackTrace();
        }*/
