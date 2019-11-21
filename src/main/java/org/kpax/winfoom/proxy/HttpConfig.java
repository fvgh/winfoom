package org.kpax.winfoom.proxy;

import org.apache.http.HttpHost;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 11/20/2019
 */
@Component
public class HttpConfig {

    private final Logger logger = LoggerFactory.getLogger(HttpConfig.class);

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    private SocketConfig socketConfig;

    private RequestConfig proxyRequestConfig;

    public RequestConfig getProxyRequestConfig() {
        if (proxyRequestConfig == null) {
            synchronized (this) {
                if (proxyRequestConfig == null) {
                    logger.debug("Create proxy request config");
                    HttpHost proxy = new HttpHost(userConfig.getProxyHost(), userConfig.getProxyPort());
                    List<String> proxyPreferredAuthSchemes = new ArrayList<>();
                    proxyPreferredAuthSchemes.add(AuthSchemes.NTLM);
                    proxyPreferredAuthSchemes.add(AuthSchemes.BASIC);
                    proxyPreferredAuthSchemes.add(AuthSchemes.DIGEST);
                    proxyPreferredAuthSchemes.add(AuthSchemes.SPNEGO);
                    proxyRequestConfig = RequestConfig.custom()
                            .setProxy(proxy)
                            .setProxyPreferredAuthSchemes(proxyPreferredAuthSchemes)
                            .setCircularRedirectsAllowed(true)
                            .build();
                }
            }
        }
        return proxyRequestConfig;
    }

    public SocketConfig getSocketConfig() {
        if (socketConfig == null) {
            synchronized (this) {
                if (socketConfig == null) {
                    logger.debug("Create socket config");
                    socketConfig = SocketConfig.custom()
                            .setTcpNoDelay(true)
                            .setSndBufSize(systemConfig.getSocketBufferSize())
                            .setRcvBufSize(systemConfig.getSocketBufferSize())
                            .build();
                }
            }
        }
        return socketConfig;
    }
}
