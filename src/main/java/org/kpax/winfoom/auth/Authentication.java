package org.kpax.winfoom.auth;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.auth.win.WindowsCredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.kpax.winfoom.config.UserConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 11/16/2019
 */
@Component
public class Authentication {

    private CredentialsProvider credentialsProvider;

    public CredentialsProvider getCredentialsProvider() {
        if (credentialsProvider == null) {
            synchronized (this) {
                if (credentialsProvider == null) {
                    credentialsProvider = new WindowsCredentialsProvider(new SystemDefaultCredentialsProvider());
                }
            }
        }
        return credentialsProvider;
    }
}
