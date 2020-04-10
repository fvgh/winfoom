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

import org.kpax.winfoom.FoomApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Eugen Covaci
 */
@Component
@PropertySource("file:${user.dir}/config/system.properties")
public class SystemConfig {

    private final Logger logger = LoggerFactory.getLogger(SystemConfig.class);

    @Value("${max.connections.per.route}")
    private Integer maxConnectionsPerRoute;

    @Value("${max.connections}")
    private Integer maxConnections;

    @Value("${internal.buffer.length}")
    private Integer internalBufferLength;

    @Value("${connection.manager.clean.interval}")
    private Integer connectionManagerCleanInterval;

    @Value("${connection.manager.idleTimeout}")
    private Integer connectionManagerIdleTimeout;

    @Value("${socket.channel.backlog}")
    private Integer socketChannelBacklog;

    @Value("${socket.channel.timeout}")
    private Integer socketChannelTimeout;

    @Value("${use.system.properties}")
    private boolean useSystemProperties;

    private String releaseVersion;

    @PostConstruct
    public void init() {
        try {
            logger.info("Get application version from manifest file");
            releaseVersion = new Manifest(FoomApplication.class.getResourceAsStream("/META-INF/MANIFEST.MF"))
                    .getMainAttributes()
                    .get(Attributes.Name.IMPLEMENTATION_VERSION).toString();
        } catch (Exception e) {
            releaseVersion = "Unknown";
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

    public Integer getSocketChannelBacklog() {
        return socketChannelBacklog;
    }

    public Integer getSocketChannelTimeout() {
        return socketChannelTimeout;
    }
}
