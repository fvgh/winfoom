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

package org.kpax.winfoom.config;

import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.auth.win.WindowsCredentialsProvider;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 1/24/2020
 */
@Configuration
class SecurityConfiguration {

    @Bean
    public CredentialsProvider credentialsProvider() {
        return new WindowsCredentialsProvider(new SystemDefaultCredentialsProvider());
    }

}
