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

public enum ProxyType {
    HTTP, SOCKS4, SOCKS5, PAC, DIRECT;

    public boolean isSocks4() {
        return this == ProxyType.SOCKS4;
    }

    public boolean isSocks5() {
        return this == ProxyType.SOCKS5;
    }

    public boolean isSocks() {
        return this == ProxyType.SOCKS4 || this == ProxyType.SOCKS5;
    }

    public boolean isHttp() {
        return this == ProxyType.HTTP;
    }

    public boolean isPac() {
        return this == ProxyType.PAC;
    }

    public boolean isDirect() {
        return this == ProxyType.DIRECT;
    }

    public ProxyInfo.Type toProxyInfoType() {
        if (!isPac()) {
            return ProxyInfo.Type.valueOf(this.toString());
        }
        return null;
    }
}
