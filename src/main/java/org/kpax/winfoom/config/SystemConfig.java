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
import org.apache.http.client.config.RequestConfig;
import org.kpax.winfoom.util.JarUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Eugen Covaci
 */
@Component
@PropertySource(value = "file:${user.home}/.winfoom/system.properties", ignoreResourceNotFound = true)
public class SystemConfig {

    private final Logger logger = LoggerFactory.getLogger(SystemConfig.class);

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    /**
     * Connection pool property:  max polled connections per route.
     */
    @Value("${maxConnections.perRoute:20}")
    private Integer maxConnectionsPerRoute;

    /**
     * Connection pool property: max polled connections.
     */
    @Value("${maxConnections:600}")
    private Integer maxConnections;

    /**
     * The max size of the entity buffer (bytes).
     */
    @Value("${internalBuffer.length:102400}")
    private Integer internalBufferLength;

    /**
     * The frequency of running purge idle
     * on the connection manager pool (seconds).
     */
    @Value("${connectionManager.clean.interval:30}")
    private Integer connectionManagerCleanInterval;

    /**
     * The connections idle timeout,
     * to be purged be a scheduled task (seconds).
     */
    @Value("${connectionManager.idleTimeout:30}")
    private Integer connectionManagerIdleTimeout;

    /**
     * The maximum number of pending connections.
     */
    @Value("${serverSocket.backlog:1000}")
    private Integer serverSocketBacklog;

    /**
     * The timeout for read/write through socket channel (seconds).
     */
    @Value("${socket.soTimeout:30}")
    private Integer socketSoTimeout;

    /**
     * The timeout for socket connect (seconds).
     */
    @Value("${socket.connectTimeout:10}")
    private Integer socketConnectTimeout;

    /**
     * Whether to use the environment properties
     * when configuring a HTTP client builder.
     */
    @Value("${useSystemProperties:false}")
    private boolean useSystemProperties;

    @Value("${releaseVersion:Unknown}")
    private String releaseVersion;

    @PostConstruct
    public void init() {
        try {
            logger.info("Get application version from manifest file");
            releaseVersion = JarUtils.getVersion(getClass());
        } catch (Exception e) {
            logger.warn("Error on getting application version from MANIFEST file, using Unknown");
        }
    }

    public Integer getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public boolean isUseSystemProperties() {
        return useSystemProperties;
    }

    public Integer getInternalBufferLength() {
        return internalBufferLength;
    }

    public Integer getConnectionManagerCleanInterval() {
        return connectionManagerCleanInterval;
    }

    public Integer getConnectionManagerIdleTimeout() {
        return connectionManagerIdleTimeout;
    }

    public Integer getServerSocketBacklog() {
        return serverSocketBacklog;
    }

    public Integer getSocketSoTimeout() {
        return socketSoTimeout;
    }

    public Integer getSocketConnectTimeout() {
        return socketConnectTimeout;
    }

    public RequestConfig.Builder applyConfig(final RequestConfig.Builder configBuilder) {
        return configBuilder.setConnectTimeout(socketConnectTimeout * 1000)
                .setConnectionRequestTimeout(socketSoTimeout * 1000)
                .setSocketTimeout(socketSoTimeout * 1000);
    }

    @PostConstruct
    public void save() throws ConfigurationException, IOException, IllegalAccessException {
        Path appPath = Paths.get(System.getProperty("user.home"), ".winfoom");
        if (!Files.exists(appPath)) {
            Files.createDirectory(appPath);
        }
        File systemProperties = appPath.resolve("system.properties").toFile();
        if (!systemProperties.exists()) {
            systemProperties.createNewFile();
            FileBasedConfigurationBuilder<PropertiesConfiguration> propertiesBuilder = new Configurations()
                    .propertiesBuilder(systemProperties);
            Configuration config = propertiesBuilder.getConfiguration();

            for (Field field : this.getClass().getDeclaredFields()) {
                Value valueAnnotation = field.getAnnotation(Value.class);
                if (valueAnnotation != null) {
                    String value = valueAnnotation.value();
                    String propName = value.replaceAll("[${}]", "").split(":")[0];
                    config.setProperty(propName, field.get(this));
                }
            }

            propertiesBuilder.save();
        }

    }

}
