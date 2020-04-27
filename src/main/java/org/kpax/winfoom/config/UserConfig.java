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

package org.kpax.winfoom.config;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.kpax.winfoom.proxy.ProxyType;
import org.kpax.winfoom.util.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * @author Eugen Covaci
 */
@Component
@PropertySource(value = "file:${user.dir}/config/user.properties", name = "userProperties")
public class UserConfig {
    private final Logger logger = LoggerFactory.getLogger(UserConfig.class);

    @Value("${local.port:3129}")
    private Integer localPort;

    @Value("${proxy.host}")
    private String proxyHost;

    @Value("${proxy.test.url}")
    private String proxyTestUrl;

    @Value("${proxy.port:0}")
    private Integer proxyPort;

    @Value("${proxy.type}")
    private ProxyType proxyType;

    @Value("${proxy.socks.username:#{null}}")
    private String proxySocksUsername;

    @Value("${proxy.socks.store.password:false}")
    private boolean proxySocksStorePassword;

    @Value("${proxy.socks.password:#{null}}")
    private String proxySocksPassword;

    @Value("${proxy.pac.fileLocation:#{null}}")
    private String proxyPacFileLocation;

    private Path tempDirectory;

    @PostConstruct
    public void init() throws IOException {
        if (StringUtils.isEmpty(proxyHost)) {
            try {
                CommandExecutor.getSystemProxy().ifPresent((s) -> {
                    logger.info("proxyLine: {}", s);
                    String[] proxies = s.split(";");
                    if (proxies.length > 0) {
                        String firstProxy = proxies[0];
                        logger.info("firstProxy: {}", firstProxy);
                        String[] elements = firstProxy.split(":");
                        if (elements.length > 1) {
                            if (elements[0].startsWith("http=")) {
                                proxyHost = elements[0].substring("http=".length());
                                proxyType = ProxyType.HTTP;
                            } else if (elements[0].startsWith("socks=")) {
                                proxyHost = elements[0].substring("socks=".length());
                                proxyType = ProxyType.SOCKS5;
                            } else {
                                proxyHost = elements[0];
                            }
                            proxyPort = Integer.parseInt(elements[1]);
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("Error on getting system proxy", e);
            }
        }
        logger.info("Check temp directory");
        if (!Files.exists(tempDirectory)) {
            logger.info("Create temp directory {}", tempDirectory);
            Files.createDirectories(tempDirectory);
        } else if (!Files.isDirectory(tempDirectory)) {
            throw new IllegalStateException(
                    String.format("The file [%s] should be a directory, not a regular file", tempDirectory));
        } else {
            logger.info("Using temp directory {}", tempDirectory);
        }
    }

    public Integer getLocalPort() {
        return localPort;
    }

    public void setLocalPort(Integer localPort) {
        this.localPort = localPort;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyTestUrl() {
        return proxyTestUrl;
    }

    public void setProxyTestUrl(String proxyTestUrl) {
        this.proxyTestUrl = proxyTestUrl;
    }

    public Path getTempDirectory() {
        return tempDirectory;
    }

    public ProxyType getProxyType() {
        return proxyType;
    }

    public void setProxyType(ProxyType proxyType) {
        this.proxyType = proxyType;
    }

    public String getProxySocksUsername() {
        return proxySocksUsername;
    }

    public void setProxySocksUsername(String proxySocksUsername) {
        this.proxySocksUsername = proxySocksUsername;
    }

    public String getProxyPassword() {
        if (StringUtils.isNotEmpty(proxySocksPassword)) {
            return new String(Base64.getDecoder().decode(proxySocksPassword));
        } else {
            return null;
        }
    }

    public void setProxyPassword(char[] proxyPassword) {
        if (proxyPassword != null && proxyPassword.length > 0) {
            proxySocksPassword = Base64.getEncoder().encodeToString(String.valueOf(proxyPassword).getBytes());
        } else {
            proxySocksPassword = null;
        }
    }

    public boolean isProxySocksStorePassword() {
        return proxySocksStorePassword;
    }

    public void setProxySocksStorePassword(boolean proxySocksStorePassword) {
        this.proxySocksStorePassword = proxySocksStorePassword;
    }

    public String getProxyPacFileLocation() {
        return proxyPacFileLocation;
    }

    public void setProxyPacFileLocation(String proxyPacFileLocation) {
        this.proxyPacFileLocation = proxyPacFileLocation;
    }

    public URL getProxyPacFileLocationAsURL() throws MalformedURLException {
        if (StringUtils.isNotEmpty(proxyPacFileLocation)) {
            if (proxyPacFileLocation.startsWith("http")) {
                return new URL(proxyPacFileLocation);
            } else {
                return new URL("file:///" + proxyPacFileLocation);
            }
        }
        return null;
    }

    @Autowired
    private void setTempDirectory(@Value("${user.home}") String userHome) {
        tempDirectory = Paths.get(userHome, ".winfoom", "temp");
    }

    public void save() throws ConfigurationException {
        FileBasedConfigurationBuilder<PropertiesConfiguration> propertiesBuilder = new Configurations()
                .propertiesBuilder(Paths.get(System.getProperty("user.dir"), "config",
                        "user.properties").toFile());
        Configuration config = propertiesBuilder.getConfiguration();
        config.setProperty("proxy.type", this.proxyType);
        config.setProperty("proxy.host", this.proxyHost);
        config.setProperty("proxy.port", this.proxyPort);
        config.setProperty("local.port", this.localPort);
        config.setProperty("proxy.test.url", this.proxyTestUrl);
        config.setProperty("proxy.socks.username", this.proxySocksUsername);
        config.setProperty("proxy.socks.store.password", this.proxySocksStorePassword);
        config.setProperty("proxy.pac.fileLocation", this.proxyPacFileLocation);

        if (this.proxySocksStorePassword) {
            config.setProperty("proxy.socks.password", this.proxySocksPassword);
        } else {

            // Clear the stored password
            if (StringUtils.isNotEmpty(proxySocksPassword)) {
                config.setProperty("proxy.socks.password", null);
                this.proxySocksPassword = null;
            }
        }

        propertiesBuilder.save();
    }

    @Override
    public String toString() {
        return "UserConfig{" +
                "localPort=" + localPort +
                ", proxyHost='" + proxyHost + '\'' +
                ", proxyTestUrl='" + proxyTestUrl + '\'' +
                ", proxyPort=" + proxyPort +
                ", proxyType=" + proxyType +
                ", proxySocksStorePassword=" + proxySocksStorePassword +
                ", tempDirectory=" + tempDirectory +
                '}';
    }
}
