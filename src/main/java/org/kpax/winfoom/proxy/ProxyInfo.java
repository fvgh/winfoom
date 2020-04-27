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

import org.apache.commons.lang3.Validate;
import org.apache.http.HttpHost;

public class ProxyInfo {

    private Type type;

    private HttpHost host;

    public ProxyInfo(Type type) {
        Validate.notNull(type, "type cannot be null");
        this.type = type;
    }

    public ProxyInfo(Type type, HttpHost host) {
        this(type);
        this.host = host;
    }

    public Type getType() {
        return type;
    }

    public HttpHost getHost() {
        return host;
    }

    @Override
    public String toString() {
        return "ProxyInfo{" +
                "type=" + type +
                ", host=" + host +
                '}';
    }

    public enum Type {
        PROXY, HTTP, HTTPS, SOCKS, SOCKS4, SOCKS5, DIRECT;

        public boolean isSocks4() {
            return this == Type.SOCKS4;
        }

        public boolean isSocks5() {
            return this == Type.SOCKS5 || this == Type.SOCKS;
        }

        public boolean isSocks() {
            return this == Type.SOCKS || this == Type.SOCKS4 || this == Type.SOCKS5;
        }

        public boolean isHttp() {
            return this == Type.HTTP || this == Type.HTTPS || this == Type.PROXY;
        }

        public boolean isDirect() {
            return this == Type.DIRECT;
        }
    }
}
