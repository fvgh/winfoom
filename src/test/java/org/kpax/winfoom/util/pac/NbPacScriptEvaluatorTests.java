package org.kpax.winfoom.util.pac;


import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.kpax.winfoom.exception.PacFileException;
import org.netbeans.core.network.proxy.pac.PacParsingException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NbPacScriptEvaluatorTests {

    @Test
    void findProxyForURL_AllHelperMethods_NoError()
            throws IOException, PacParsingException, URISyntaxException, PacFileException {
        String pacContent = IOUtils.toString(
                Thread.currentThread().getContextClassLoader().
                        getResourceAsStream("proxy-simple-all-helpers.pac"),
                StandardCharsets.UTF_8);
        NbPacScriptEvaluator nbPacScriptEvaluator = new NbPacScriptEvaluator(pacContent);
        String proxyForURL = nbPacScriptEvaluator.findProxyForURL(new URI("http://host:80/path?param1=val"));
        assertEquals("DIRECT", proxyForURL);
    }
}
