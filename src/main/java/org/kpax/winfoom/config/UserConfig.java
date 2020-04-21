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

    @Value("${proxy.socks.username}")
    private String proxySocksUsername;

    @Value("${proxy.socks.store.password}")
    private boolean proxySocksStorePassword;

    @Value("${proxy.socks.password}")
    private String proxySocksPassword;

    private Path tempDirectory;

    @PostConstruct
    public void init() throws IOException {
        if (StringUtils.isEmpty(proxyHost)) {
            try {
                CommandExecutor.getSystemProxy().ifPresent((s) -> {
                    logger.info("proxyLine: {}", s);
                    String[] split = s.split(":");
                    proxyHost = split[0];
                    proxyPort = Integer.parseInt(split[1]);
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

    public boolean isSocks() {
        return proxyType == ProxyType.SOCKS5;
    }

    public boolean isHttp() {
        return proxyType == ProxyType.HTTP;
    }

    public String getProxySocksUsername() {
        return proxySocksUsername;
    }

    public void setProxySocksUsername(String proxySocksUsername) {
        this.proxySocksUsername = proxySocksUsername;
    }

    public String getProxyPassword() {
        if (proxySocksPassword != null) {
            return new String(Base64.getDecoder().decode(proxySocksPassword));
        }
        return null;
    }

    public void setProxyPassword(char[] proxyPassword) {
        if (proxyPassword != null) {
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
        if (this.proxySocksStorePassword) {
            config.setProperty("proxy.socks.password", this.proxySocksPassword);
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
