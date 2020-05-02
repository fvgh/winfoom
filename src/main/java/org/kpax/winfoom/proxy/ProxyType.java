package org.kpax.winfoom.proxy;

public interface ProxyType {

    boolean isSocks4();

    boolean isSocks5();

    default boolean isSocks() {
        return isSocks4() || isSocks5();
    }

    boolean isHttp();

    boolean isDirect();

}
