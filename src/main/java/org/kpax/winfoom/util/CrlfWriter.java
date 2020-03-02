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

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/2/2020
 */
public class CrlfWriter {

    private OutputStream out;

    public CrlfWriter(OutputStream out) {
        Validate.notNull(out, "out cannot be null");
        this.out = out;
    }

    public CrlfWriter write (Object obj) throws IOException {
        out.write(CrlfFormat.format(obj));
        return this;
    }

    public CrlfWriter writeEmptyLine() throws IOException {
        out.write(CrlfFormat.CRLF.getBytes());
        return this;
    }

    public OutputStream getOut() {
        return out;
    }
}
