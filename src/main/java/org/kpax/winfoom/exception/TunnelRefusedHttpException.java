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

package org.kpax.winfoom.exception;

import org.apache.commons.lang3.Validate;
import org.apache.hc.client5.http.impl.TunnelRefusedException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 4/6/2020
 */
public class TunnelRefusedHttpException extends HttpException {

    private ClassicHttpResponse httpResponse;

    public TunnelRefusedHttpException(ClassicHttpResponse httpResponse) {
        super("CONNECT refused", httpResponse);
    }

    public TunnelRefusedHttpException(String message, ClassicHttpResponse httpResponse) {
        super(message);
        Validate.notNull(httpResponse, "httpResponse cannot be null");
        this.httpResponse = httpResponse;
    }

    public ClassicHttpResponse getResponse() {
        return httpResponse;
    }
}
