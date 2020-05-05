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
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.exception.InvalidPacFileException;
import org.kpax.winfoom.util.HttpUtils;
import org.netbeans.core.network.proxy.pac.impl.NbPacScriptEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Responsible for loading the PAC script file and executing the {@code findProxyForURL} method.
 */
@Component
class ProxyAutoConfig {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ProxyConfig proxyConfig;

    /**
     * The NetBeans implementation of a PAC script evaluator.
     */
    private NbPacScriptEvaluator nbPacScriptEvaluator;

    /**
     * It loads and parse the PAC script file.
     *
     * @return the {@link NbPacScriptEvaluator} instance.
     * @throws IOException
     * @throws InvalidPacFileException
     */
    synchronized NbPacScriptEvaluator loadScript() throws IOException, InvalidPacFileException {
        URL url = proxyConfig.getProxyPacFileLocationAsURL();
        if (url == null) {
            throw new IllegalStateException("No proxy PAC file location found");
        }
        logger.info("Get PAC file from: {}", url);
        try (InputStream inputStream = url.openStream()) {
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            logger.info("PAC content: {}", content);
            try {
                nbPacScriptEvaluator = new NbPacScriptEvaluator(content);
            } catch (Exception e) {
                throw new InvalidPacFileException("The provided PAC file is not valid", e);
            }
        }
        return nbPacScriptEvaluator;
    }

    private NbPacScriptEvaluator getPacScriptEvaluator() {
        if (nbPacScriptEvaluator == null) {
            throw new IllegalStateException("Proxy PAC file not loaded");
        }
        return nbPacScriptEvaluator;
    }

    boolean isLoaded() {
        return nbPacScriptEvaluator != null;
    }

    /**
     * Calls the {@code findProxyForURL} method within the PAC script file
     * @param uri the request URI.
     * @return the list of {@link ProxyInfo}.
     * @throws InvalidPacFileException
     */
    List<ProxyInfo> findProxyForURL(URI uri) throws InvalidPacFileException {
        String proxyLine = getPacScriptEvaluator().callFindProxyForURL(uri);
        logger.debug("proxyLine [{}]", proxyLine);
        return HttpUtils.parsePacProxyLine(proxyLine);
    }
}
