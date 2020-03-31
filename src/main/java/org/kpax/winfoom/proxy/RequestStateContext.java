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

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 3/30/2020
 */
class RequestStateContext {

    RequestState state = RequestState.RECEIVED;

    RequestState getState() {
        return state;
    }

    void nextState() {
        this.state = state.nextState();
    }

    enum RequestState {
        RECEIVED, INITIATED, EXECUTED, PROCESS_RESPONSE;

        RequestState nextState() {
            int currentIndex = this.ordinal();
            RequestState[] values = values();
            if (currentIndex < values.length - 1) {
                return values[currentIndex + 1];
            } else {
                throw new IndexOutOfBoundsException(currentIndex);
            }
        }

        boolean isEqualsOrBefore(RequestState state) {
            return this.ordinal() <= state.ordinal();
        }

    }
}
