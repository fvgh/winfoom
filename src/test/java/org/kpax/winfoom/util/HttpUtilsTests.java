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

import org.apache.http.HttpRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kpax.winfoom.proxy.ProxyInfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/16/2020
 */

class HttpUtilsTests {

    @Test
    void parseUri_noQueryParams_ParseOk() throws URISyntaxException {
        String uri = "http://happy/people";
        URI result = HttpUtils.parseUri(uri);
        Assertions.assertEquals(uri, result.toString());
        Assertions.assertEquals("/people", result.getPath());
    }

    @Test
    void parseUri_oneQueryParam_ParseOk() throws URISyntaxException {
        String uri = "http://happy/people?meIncluded=not";
        URI result = HttpUtils.parseUri(uri);
        Assertions.assertEquals(uri, result.toString());
        Assertions.assertEquals("/people", result.getPath());
        Assertions.assertEquals("meIncluded=not", result.getQuery());
    }

    @Test
    void parseUri_multipleQueryParam_ParseOk() throws URISyntaxException {
        String uri = "http://happy/people?meIncluded=not&youIncluded=maybe";
        URI result = HttpUtils.parseUri(uri);
        Assertions.assertEquals(uri, result.toString());
        Assertions.assertEquals("/people", result.getPath());
        Assertions.assertEquals("meIncluded=not&youIncluded=maybe", result.getQuery());
    }

    @Test
    void parseUri_noQueryParamQuestionMark_ParseOk() throws URISyntaxException {
        String uri = "http://happy/people?";
        URI result = HttpUtils.parseUri(uri);
        Assertions.assertEquals("http://happy/people", result.toString());
        Assertions.assertNull(result.getQuery());
    }

    @Test
    void parseContentType_withCharset_NoError() {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Content-Type", "text/plain; charset=ISO-8859-1");
        ContentType contentType = HttpUtils.getContentType(request);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals("ISO-8859-1", contentType.getCharset().name());
    }

    @Test
    void parseContentType_NoCharset_NoError() {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Content-Type", "text/plain");
        ContentType contentType = HttpUtils.getContentType(request);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertNull(contentType.getCharset());
    }


    @Test
    void parseContentType_NoCharsetOtherToken_NoError() {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Content-Type", "text/plain; x=y; z=k");
        ContentType contentType = HttpUtils.getContentType(request);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertNull(contentType.getCharset());
    }

    @Test
    void parsePacProxyLine_directOnly_DIRECT () {
        String proxyLine = "DIRECT";
        List<ProxyInfo> proxyInfos = HttpUtils.parsePacProxyLine(proxyLine);
        Assertions.assertEquals(1, proxyInfos.size());
        Assertions.assertEquals(ProxyInfo.Type.DIRECT, proxyInfos.get(0).getType());
        Assertions.assertNull(proxyInfos.get(0).getHost());
    }

    @Test
    void parsePacProxyLine_proxyThenDirect_DIRECT () {
        String proxyLine = "PROXY localhost:1080;DIRECT";

        List<ProxyInfo> proxyInfos = HttpUtils.parsePacProxyLine(proxyLine);
        Assertions.assertEquals(2, proxyInfos.size());
        Assertions.assertEquals(ProxyInfo.Type.PROXY, proxyInfos.get(0).getType());
        Assertions.assertEquals("localhost:1080", proxyInfos.get(0).getHost().toHostString());
        Assertions.assertEquals(ProxyInfo.Type.DIRECT, proxyInfos.get(1).getType());
        Assertions.assertNull(proxyInfos.get(1).getHost());
    }

}
