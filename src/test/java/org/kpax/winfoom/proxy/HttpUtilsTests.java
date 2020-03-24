package org.kpax.winfoom.proxy;

import org.apache.http.HttpException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kpax.winfoom.util.HttpUtils;

import java.io.IOException;

class HttpUtilsTests {

    @Test
    void isClientException_HttpException_True () {
        boolean isClientException = HttpUtils.isClientException(HttpException.class);
        Assertions.assertTrue(isClientException);
    }

    @Test
    void isClientException_IOException_False () {
        boolean isClientException = HttpUtils.isClientException(IOException.class);
        Assertions.assertFalse(isClientException);
    }

    @Test
    void isClientException_TunnelRefusedException_True () {
        boolean isClientException = HttpUtils.isClientException(IOException.class);
        Assertions.assertFalse(isClientException);
    }
}
