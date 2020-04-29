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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProxyBlacklist {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<ProxyInfo, Instant> blacklistMap = new ConcurrentHashMap<>();

    public void blacklist(ProxyInfo proxyInfo) {
        logger.debug("Blacklist proxy {}", proxyInfo);
        blacklistMap.putIfAbsent(proxyInfo, Instant.now());
    }

    public boolean isBlacklisted(ProxyInfo proxyInfo) {
        Instant instant = blacklistMap.get(proxyInfo);
        if (instant != null) {
            Instant now = Instant.now();
            return instant.plus(1, ChronoUnit.HOURS).isAfter(now);// FIXME Configurable
        }
        return false;
    }

    public void clear() {
        blacklistMap.clear();
    }

}
