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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class HeaderDateGenerator {

    /**
     * Date format pattern used to generate the header in RFC 1123 format.
     */
    public static final
    String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

    /**
     * The time zone to use in the date header.
     */
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    public String getCurrentDate() {
        DateFormat dateformat = new SimpleDateFormat(PATTERN_RFC1123, Locale.US);
        dateformat.setTimeZone(GMT);
        return dateformat.format(new Date());
    }

}
