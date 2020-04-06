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

import java.nio.file.Paths;

import javax.annotation.PostConstruct;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.kpax.winfoom.util.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

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

    @PostConstruct
    public void init() {
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

    public void save() throws ConfigurationException {
        FileBasedConfigurationBuilder<PropertiesConfiguration> propertiesBuilder = new Configurations()
                .propertiesBuilder(Paths.get(System.getProperty("user.dir"), "config",
                        "user.properties").toFile());
        Configuration config = propertiesBuilder.getConfiguration();
        config.setProperty("local.port", this.localPort);
        config.setProperty("proxy.host", this.proxyHost);
        config.setProperty("proxy.port", this.proxyPort);
        config.setProperty("proxy.test.url", this.proxyTestUrl);
        propertiesBuilder.save();
    }

    @Override
    public String toString() {
        return "UserConfig{" +
                "localPort=" + localPort +
                ", proxyHost='" + proxyHost + '\'' +
                ", proxyTestUrl='" + proxyTestUrl + '\'' +
                ", proxyPort=" + proxyPort +
                '}';
    }
}
