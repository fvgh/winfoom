package org.kpax.winfoom.proxy;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kpax.winfoom.FoomApplicationTest;
import org.kpax.winfoom.TestConstants;
import org.kpax.winfoom.config.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kpax.winfoom.TestConstants.LOCAL_PROXY_PORT;
import static org.kpax.winfoom.TestConstants.PROXY_PORT;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = FoomApplicationTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(10)
public class DirectProxyClientConnectionTests {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int socketTimeout = 3; // seconds

    @MockBean
    private ProxyConfig proxyConfig;

    @Autowired
    private ClientConnectionHandler clientConnectionHandler;

    @Autowired
    private ConnectionPoolingManager connectionPoolingManager;

    private ServerSocket serverSocket;

    private HttpServer remoteServer;

    @BeforeEach
    void beforeEach() {
        when(proxyConfig.getProxyHost()).thenReturn("localhost");
        when(proxyConfig.getProxyPort()).thenReturn(PROXY_PORT);
        when(proxyConfig.getProxyType()).thenReturn(ProxyConfig.Type.DIRECT);
    }

    @BeforeAll
    void before() throws IOException {
        remoteServer = ServerBootstrap.bootstrap().registerHandler("/get", new HttpRequestHandler() {

            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                    throws IOException {
                response.setEntity(new StringEntity("12345"));
            }

        }).create();
        remoteServer.start();

        serverSocket = new ServerSocket(TestConstants.LOCAL_PROXY_PORT);
        connectionPoolingManager.start();
        new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(socketTimeout * 1000);
                    new Thread(() -> {

                        // Handle this connection.
                        try {
                            clientConnectionHandler.handleConnection(socket);
                        } catch (Exception e) {
                            logger.error("Error on handling connection", e);
                        }
                    }).start();
                } catch (SocketException e) {
                    if (!StringUtils.startsWithIgnoreCase(e.getMessage(), "Interrupted function call")) {
                        logger.error("Socket error on getting connection", e);
                    }
                } catch (Exception e) {
                    logger.error("Error on getting connection", e);
                }
            }
        }).start();

    }

    @Test
    @Order(0)
    void directProxy_NonConnect_CorrectResponse() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(localProxy)
                    .build();
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpGet request = new HttpGet("/get");
            request.setConfig(config);

            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                assertEquals("12345", responseBody);
            }
        }
    }

    @Test
    @Order(1)
    void directProxy_Connect_200OK() throws IOException {
        HttpHost localProxy = new HttpHost("localhost", LOCAL_PROXY_PORT, "http");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setProxy(localProxy).build()) {
            HttpHost target = HttpHost.create("http://localhost:" + remoteServer.getLocalPort());
            HttpRequest request = new BasicHttpRequest("CONNECT", "localhost:" + remoteServer.getLocalPort());
            try (CloseableHttpResponse response = httpClient.execute(target, request)) {
                assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            }
        }
    }

    @AfterAll
    void after() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Ignore
        }
        remoteServer.stop();
    }
}
