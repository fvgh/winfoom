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
public class SecurityConfiguration {

    @Bean
    public CredentialsProvider credentialsProvider () {
        return new WindowsCredentialsProvider(new SystemDefaultCredentialsProvider());
    }
}
