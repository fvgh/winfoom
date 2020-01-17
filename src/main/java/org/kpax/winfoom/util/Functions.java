/*******************************************************************************
 * Copyright (c) 2018 Eugen Covaci.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 *
 * Contributors:
 *     Eugen Covaci - initial design and implementation
 *******************************************************************************/

package org.kpax.winfoom.util;

import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * @author Eugen Covaci
 */
public class Functions {

    /**
     * Repeats the action until the stop condition becomes <code>true</code>.
     *
     * @param action The action to be repeated.
     * @param until  The stop condition.
     * @param times  The maximum repeat times.
     * @return The result.
     * @throws Exception
     */
    public static <R extends Closeable> Optional<R> repeat(Callable<R> action, Predicate<R> until, int times)
            throws Exception {
        R r = null;
        for (int i = 0; i < times; i++) {
            r = action.call();
            if (until.test(r)) {
                break;
            } else {
                LocalIOUtils.close(r);
            }
        }
        return Optional.of(r);
    }

}
