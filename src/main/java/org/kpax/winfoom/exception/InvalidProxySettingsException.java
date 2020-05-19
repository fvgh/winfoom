package org.kpax.winfoom.exception;

import org.apache.http.HttpException;

public class InvalidProxySettingsException extends HttpException {

    public InvalidProxySettingsException(String message) {
        super(message);
    }

    public InvalidProxySettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
