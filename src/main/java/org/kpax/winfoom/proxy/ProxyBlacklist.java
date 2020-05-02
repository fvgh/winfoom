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

import org.kpax.winfoom.config.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProxyBlacklist {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Key = the blacklisted ProxyInfo<br>
     * Value = the blacklist timeout Instant
     */
    private final Map<ProxyInfo, Instant> blacklistMap = new ConcurrentHashMap<>();

    private final ChronoUnit temporalUnit = ChronoUnit.MINUTES;

    @Autowired
    private ProxyConfig proxyConfig;

    Instant blacklist(ProxyInfo proxyInfo) {
        logger.debug("Attempt to blacklist proxy {}", proxyInfo);
        return blacklistMap.compute(proxyInfo, (key, value) -> {
            Instant now = Instant.now();
            if (value == null || value.isBefore(now)) {
                Instant timeoutInstant = now.plus(proxyConfig.getBlacklistTimeout(),
                        temporalUnit);
                logger.debug("Blacklisted until {}", timeoutInstant);
                return timeoutInstant;
            } else {
                logger.debug("Already blacklisted until {}", value);
                return value;
            }
        });
    }

    /**
     * It verifies whether a proxy is blacklisted.<br>
     * If the proxy is in the blacklist map but expired, will be removed.
     *
     * @param proxyInfo the proxy to be checked
     * @return <code>true</code> iff the proxy is blacklisted
     */
    boolean checkBlacklist(ProxyInfo proxyInfo) {
        Instant timeoutInstant = blacklistMap.computeIfPresent(proxyInfo, (key, value) -> {
            return value.isBefore(Instant.now()) ? null : value;
        });
        return timeoutInstant != null;
    }

    public int clear() {
        long count = blacklistMap.keySet().stream()
                .filter(this::checkBlacklist).count();
        blacklistMap.clear();
        return (int) count;
    }

    public ChronoUnit getTemporalUnit () {
        return temporalUnit;
    }

    public Map<ProxyInfo, Instant> getBlacklistMap() {
        return Collections.unmodifiableMap(blacklistMap);
    }
}
