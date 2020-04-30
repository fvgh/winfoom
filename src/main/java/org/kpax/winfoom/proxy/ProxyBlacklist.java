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

import org.kpax.winfoom.config.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class ProxyBlacklist {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<ProxyInfo, Instant> blacklistMap = new ConcurrentHashMap<>();

    @Autowired
    private UserConfig userConfig;

    Instant blacklist(ProxyInfo proxyInfo) {
        logger.debug("Attempt to blacklist proxy {}", proxyInfo);
        return blacklistMap.compute(proxyInfo, (key, value) -> {
            Instant now = Instant.now();
            if (value == null || value.plus(userConfig.getBlacklistTimeout(),
                    ChronoUnit.MINUTES).isBefore(now)) {
                Instant timeoutInstant = now.plus(userConfig.getBlacklistTimeout(),
                        ChronoUnit.MINUTES);
                logger.debug("Blacklisted until {}", timeoutInstant);
                return timeoutInstant;
            } else {
                logger.debug("Already blacklisted until {}", value);
                return value;
            }
        });
    }

    boolean isBlacklisted(ProxyInfo proxyInfo) {
        Instant instant = blacklistMap.get(proxyInfo);

        if (instant != null && instant.plus(userConfig.getBlacklistTimeout(),
                ChronoUnit.MINUTES).isAfter(Instant.now())) {
            return true;
        }
        return false;
    }

    int clear() {
        Instant now = Instant.now();
        long count = blacklistMap.values().stream()
                .filter(i -> i.plus(userConfig.getBlacklistTimeout(),
                        ChronoUnit.MINUTES).isAfter(Instant.now())).count();
        blacklistMap.clear();
        return (int) count;
    }

}
