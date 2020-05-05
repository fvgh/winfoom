package org.kpax.winfoom.proxy;

/**
 * The contract of any proxy type.
 */
public interface ProxyType {

    /**
     * @return {@code true} iff the proxy type is SOCKS4.
     */
    boolean isSocks4();

    /**
     * @return {@code true} iff the proxy type is SOCKS5.
     */
    boolean isSocks5();

    /**
     * @return {@code true} iff the proxy type is SOCKS (version 4 or 5).
     */
    default boolean isSocks() {
        return isSocks4() || isSocks5();
    }

    /**
     * @return {@code true} iff the proxy type is HTTP.
     */
    boolean isHttp();

    /**
     * @return {@code true} iff there is no proxy.
     */
    boolean isDirect();

}
