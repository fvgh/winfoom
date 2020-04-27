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

import org.apache.commons.io.IOUtils;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.exception.InvalidPacFileException;
import org.kpax.winfoom.util.ProxyAutoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class PacFile {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private UserConfig userConfig;

    private String content;

    public synchronized String loadContent() throws IOException, InvalidPacFileException {
        URL url = userConfig.getProxyPacFileLocationAsURL();
        if (url == null) {
            throw new IllegalStateException("No proxy PAC file location found");
        }
        logger.info("Get PAC file from: {}", url);
        try (InputStream inputStream = url.openStream()) {
            this.content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            logger.debug("PAC content: {}", content);
            try {
                String proxyTestLine = ProxyAutoConfig.evaluate(content, "http://localhost:80", "localhost");
                logger.debug("proxyTestLine: {}", proxyTestLine);
            } catch (Exception e) {
                logger.error("Error on testing PAC file", e);
                throw new InvalidPacFileException("The provided PAC file is not valid", e);
            }
        }
        return this.content;
    }

    public String getContent() {
        if (content == null) {
            throw new IllegalStateException("Proxy PAC file not loaded");
        }
        return content;
    }

    public boolean isLoaded() {
        return content != null;
    }
}
