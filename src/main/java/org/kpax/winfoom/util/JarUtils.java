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

import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class JarUtils {

    private JarUtils() {
    }

    /**
     * Get the {@code Implementation-Version}  attribute from jar's MANIFEST.MF file.
     * Only accurate when the application is packaged as JAR file.
     *
     * @return the application's version as it appears in the pom.xml file.
     * @throws IOException
     */
    @Deprecated
    public static String getAppVersion() throws IOException {
        Object version = new Manifest(JarUtils.class.getResourceAsStream("/META-INF/MANIFEST.MF"))
                .getMainAttributes()
                .get(Attributes.Name.IMPLEMENTATION_VERSION);
        if (version != null) {
            return version.toString();
        } else {
            throw new IllegalStateException("Version not found withing the MANIFEST.MF file");
        }
    }
}
