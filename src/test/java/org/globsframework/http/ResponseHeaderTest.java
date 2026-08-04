package org.globsframework.http;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.impl.DefaultGlobTypeBuilder;
import org.globsframework.core.model.Glob;
import org.globsframework.core.utils.Ref;
import org.globsframework.http.model.HttpHeader;
import org.globsframework.http.server.apache.GlobHttpApacheBuilder;
import org.globsframework.http.server.apache.Server;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Response headers, in both directions.
 * <p>
 * Both behaviours checked here used to be broken: headers declared through {@code addHeader} were never
 * written (the only code doing it lived in the commented-out pre-httpcore5 handler), and request headers
 * were matched case-sensitively although RFC 9110 makes them case-insensitive.
 */
public class ResponseHeaderTest {
    private AsyncServerBootstrap bootstrap;
    private HttpAsyncServer server;
    private int port;
    private HttpServerRegister httpServerRegister;

    @Before
    public void init() {
        bootstrap = AsyncServerBootstrap.bootstrap()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoReuseAddress(true)
                        .setSoTimeout(15000, TimeUnit.MILLISECONDS)
                        .build());
        httpServerRegister = new HttpServerRegister("TestServer/1.1");
    }

    @After
    public void tearDown() throws InterruptedException {
        if (server != null) {
            server.initiateShutdown();
            server.awaitShutdown(TimeValue.of(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void declaredHeadersAreWrittenOnTheResponse() throws IOException {
        httpServerRegister.register("/declared", null)
                .get(null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(Content.TYPE.instantiate()
                                .set(Content.value, "ok")))
                .addHeader("X-Served-By", "globs");
        start();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            client.execute(new HttpGet("http://localhost:" + port + "/declared"), response -> {
                Assert.assertEquals("globs", response.getFirstHeader("X-Served-By").getValue());
                return null;
            });
        }
    }

    @Test
    public void aHandlerCanSetHeadersItOnlyKnowsPerRequest() throws IOException {
        httpServerRegister.register("/dynamic", null)
                .get(null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(GlobHttpContent.TYPE.instantiate()
                                .set(GlobHttpContent.content, "{}".getBytes(StandardCharsets.UTF_8))
                                .set(GlobHttpContent.mimeType, "application/json")
                                .set(GlobHttpContent.statusCode, 201)
                                .set(GlobHttpContent.headers, new Glob[]{
                                        HttpHeader.create("X-Session-Id", "abc-123"),
                                        HttpHeader.create("Location", "/dynamic/1")})));
        start();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            client.execute(new HttpGet("http://localhost:" + port + "/dynamic"), (ClassicHttpResponse response) -> {
                Assert.assertEquals(201, response.getCode());
                Assert.assertEquals("abc-123", response.getFirstHeader("X-Session-Id").getValue());
                Assert.assertEquals("/dynamic/1", response.getFirstHeader("Location").getValue());
                return null;
            });
        }
    }

    @Test
    public void requestHeadersAreMatchedIgnoringCase() throws IOException {
        Ref<Glob> seen = new Ref<>();
        httpServerRegister.register("/header", null)
                .get(null, HeaderType.TYPE, (body, url, queryParameters, header) -> {
                    seen.set(header);
                    return CompletableFuture.completedFuture(Content.TYPE.instantiate()
                            .set(Content.value, "ok"));
                });
        start();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet("http://localhost:" + port + "/header");
            // the field is declared "X-Trace-Id"; a client is free to send any casing
            get.addHeader("x-TRACE-id", "42");
            client.execute(get, response -> null);
        }
        Assert.assertEquals("42", seen.get().get(HeaderType.traceId));
    }

    private void start() {
        Server serverInstance = new GlobHttpApacheBuilder(httpServerRegister)
                .startAndWaitForStartup(bootstrap, 0);
        server = serverInstance.getServer();
        port = serverInstance.getPort();
    }

    public static class Content {
        public static final GlobType TYPE;
        public static final org.globsframework.core.metamodel.fields.StringField value;

        static {
            GlobTypeBuilder typeBuilder = DefaultGlobTypeBuilder.init("Content");
            value = typeBuilder.declareStringField("value");
            TYPE = typeBuilder.build();
        }
    }

    public static class HeaderType {
        public static final GlobType TYPE;
        public static final org.globsframework.core.metamodel.fields.StringField traceId;

        static {
            GlobTypeBuilder typeBuilder = DefaultGlobTypeBuilder.init("HeaderType");
            traceId = typeBuilder.declareStringField("X-Trace-Id");
            TYPE = typeBuilder.build();
        }
    }
}
