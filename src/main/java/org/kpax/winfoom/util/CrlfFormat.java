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

package org.kpax.winfoom.util;

import org.apache.http.ProtocolVersion;

/**
 * @author Eugen Covaci
 */
public final class CrlfFormat {

    public static final String CRLF = "\r\n";

    private CrlfFormat() {
    }

    public static byte[] format(Object input) {
        if (input != null) {
            return (input + CRLF).getBytes();
        }
        return CRLF.getBytes();
    }

    public static byte[] toStatusLine(ProtocolVersion protocolVersion, int httpCode) {
        return format(HttpUtils.toStatusLine(protocolVersion, httpCode));
    }

    public static byte[] toStatusLine(int httpCode) {
        return format(HttpUtils.toStatusLine(httpCode));
    }

    public static byte[] to500StatusLine(ProtocolVersion protocolVersion) {
        return format(HttpUtils.to500StatusLine(
                protocolVersion));
    }

    public static byte[] to500StatusLine() {
        return format(HttpUtils.to500StatusLine());
    }

}
