package org.globsframework.http;

import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.entity.compress.DecompressingEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.impl.bootstrap.AsyncServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.annotations.*;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.metamodel.impl.DefaultGlobTypeBuilder;
import org.globsframework.core.model.Glob;
import org.globsframework.core.utils.Ref;
import org.globsframework.core.utils.collections.Pair;
import org.globsframework.http.model.*;
import org.globsframework.http.openapi.model.GetOpenApiParamType;
import org.globsframework.http.openapi.model.GlobOpenApi;
import org.globsframework.http.openapi.model.OpenApiType;
import org.globsframework.http.server.apache.GlobHttpApacheBuilder;
import org.globsframework.http.server.apache.Server;
import org.globsframework.json.GSonUtils;
import org.globsframework.json.annottations.JsonHideValue;
import org.globsframework.json.annottations.JsonHideValue_;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class GlobHttpRequestHandlerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("test");

    private AsyncServerBootstrap bootstrap;
    private HttpAsyncServer server;
    private int port;
    private HttpServerRegister httpServerRegister;
    private GlobOpenApi globOpenApi;
    private BlockingQueue<Pair<Glob, Glob>> pairs;

    @Before
    public void init() {
        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoReuseAddress(true)
                .setSoTimeout(15000, TimeUnit.MILLISECONDS)
                .setTcpNoDelay(true)
                .build();

        bootstrap = AsyncServerBootstrap.bootstrap()
                .setIOReactorConfig(config);

        httpServerRegister = new HttpServerRegister("TestServer/1.1");
        globOpenApi = new GlobOpenApi(httpServerRegister);

        pairs = new LinkedBlockingDeque<>();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (server != null) {
            server.initiateShutdown();
            server.awaitShutdown(TimeValue.of(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void name() throws IOException, InterruptedException, ParseException {
        String[] activeId = new String[]{""}; // Used to store an identifier of the request handler responding.
        Ref<Glob> headers = new Ref<>();

        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    activeId[0] = "/test/{id}/TOTO/{subId}";
                    return null;
                })
                .withHeaderType(HeaderType.TYPE)
        ;

        httpServerRegister.register("/test/{id}", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, HeaderType.TYPE, (body, url, queryParameters, header) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    activeId[0] = "/test/{id}";
                    headers.set(header);
                    return CompletableFuture.completedFuture(ResponseWithSensibleData.TYPE.instantiate()
                            .set(ResponseWithSensibleData.field1, "azertyu")
                            .set(ResponseWithSensibleData.field2, "qsdfghjkl")
                    );
                })
                .withSensitiveData(true);

        httpServerRegister.register("/test/{id}/TOTO", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    activeId[0] = "/test/{id}/TOTO";
                    return CompletableFuture.completedFuture(null);
                });

        httpServerRegister.register("/query/{id}", URLOneParameter.TYPE)
                .get(null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                                .set(Response1.value, "some important information."))
                )
                .declareReturnType(Response1.TYPE);

        httpServerRegister.register("/post", null)
                .post(BodyContent.TYPE, null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(null)
                );

        httpServerRegister.register("/put/{id}", URLOneParameter.TYPE)
                .put(BodyContent.TYPE, null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(null)
                );

        httpServerRegister.register("/delete/{id}", URLOneParameter.TYPE)
                .delete(null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                                .set(BodyContent.DATA, "some important information.")
                        )
                )
                .declareReturnType(BodyContent.TYPE);

        httpServerRegister.register("/path/{path}", URLWithArray.TYPE)
                .get(null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                                .set(BodyContent.DATA, "Get with " + String.join(",", url.get(URLWithArray.path)))
                        )
                )
                .declareReturnType(BodyContent.TYPE);

        httpServerRegister.register("/test-custom-status-code", null)
                .patch(BodyContent.TYPE, null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(CustomBodyWithStatusCode.TYPE.instantiate()
                                .set(CustomBodyWithStatusCode.field1, 201)
                                .set(CustomBodyWithStatusCode.field2, BodyContent.TYPE.instantiate()
                                        .set(BodyContent.DATA, "custom data works")
                                )
                        )
                )
                .declareReturnType(BodyContent.TYPE);

//        interface DataFunctor {
//            void onData(ByteBuffer data);
//
//            void complete();
//        }
//
//        interface ResponseFunctor {
//            void onResponse(ByteBuffer response);
//
//            void complete(int statusCode);
//        }
//
//        httpServerRegister.registerAsync("", null)
//                .post(null, null, new HttpTreatment() {
//                    public DataFunctor consume(Glob url, Glob queryParameters, ResponseFunctor responseFunctor) throws Exception {
//
//                    }
//                });

        httpServerRegister.register("/binaryCall", null)
                .postBin(null, null, (body, url, queryParameters, headerType) -> {
                    final InputStream inputStream = body.asStream().stream();
                    return CompletableFuture.completedFuture(HttpOutputData.asStream(inputStream, body.asStream().size()));
                });

        httpServerRegister.register("/binaryCall", null)
                .getBin(null, null, (body, url, queryParameters, headerType) -> CompletableFuture.completedFuture(
                        HttpOutputData.asGlobArray(new Glob[] {
                                Response1.TYPE.instantiate().set(Response1.value, "d1") ,
                                Response1.TYPE.instantiate().set(Response1.value, "d2") })));


        startServer();

        GlobOpenApi globOpenApi = new GlobOpenApi(httpServerRegister);
        Glob openApiDoc = globOpenApi.getOpenApiDoc();
        String encode = GSonUtils.encode(openApiDoc, false);
        System.out.println(encode);

        HttpHost target = new HttpHost("http", "localhost", port);

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = GlobHttpUtils.createGet("/test/123/TOTO/4567", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
            Pair<Glob, Glob> poll = pairs.poll(2, TimeUnit.SECONDS);
            Assert.assertNotNull(poll);
            Assert.assertEquals(123, poll.getFirst().get(URLParameter.ID, 0));
            Assert.assertEquals(4567, poll.getFirst().get(URLParameter.SUBID, 0));
            Assert.assertEquals("ZERZE", poll.getSecond().get(QueryParameter.NAME));
            Assert.assertArrayEquals(new String[]{"A", "B", "C", "D"}, poll.getSecond().get(QueryParameter.INFO));
            Assert.assertEquals("AAAZZZ", poll.getSecond().get(QueryParameter.param).get(QueryParameter.NAME));
            Assert.assertEquals("/test/{id}/TOTO/{subId}", activeId[0]);
        }

        // regression (#3): a path longer than any registered exact route must not prefix-match a shorter one
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/test/123/TOTO/4567/EXTRA");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(403, httpResponse.code());
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = GlobHttpUtils.createGet("/test/123/TOTO", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
            Assert.assertEquals("/test/{id}/TOTO", activeId[0]);
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/test/123/TOTO?name=-10%25%20sur%20les%20articles%20de%20la%20saison%20hiver%202022");
//                    GlobHttpUtils.createGet("/test/123/TOTO", QueryParameter.TYPE.instantiate()
//                    .set(QueryParameter.NAME, "-10%%20sur%20les%20articles%20de%20la%20saison%20hiver%202022"));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
            Assert.assertEquals("/test/{id}/TOTO", activeId[0]);
            Pair<Glob, Glob> poll = pairs.poll(2, TimeUnit.SECONDS);
            Assert.assertNotNull(poll);
            Assert.assertEquals("-10% sur les articles de la saison hiver 2022", poll.getSecond().get(QueryParameter.NAME));
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = GlobHttpUtils.createGet("/test/123", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            httpRequest.addHeader(FieldName.getName(HeaderType.name), "my header");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals("/test/{id}", activeId[0]);
            Assert.assertNotNull(headers.get());
            Assert.assertEquals("my header", headers.get().get(HeaderType.name));
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/query/123");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals("{\"value\":\"some important information.\"}", httpResponse.body());
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpRequest = new HttpPost("/post");
            httpRequest.setEntity(new StringEntity(GSonUtils.encode(BodyContent.TYPE.instantiate()
                    .set(BodyContent.DATA, ""), false
            )));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpRequest = new HttpPost("/post");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpRequest = new HttpPost("/binaryCall");
            httpRequest.setEntity(new StringEntity("Some data send"));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals("Some data send", httpResponse.body());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/binaryCall");
            httpRequest.setEntity(new StringEntity("Some data send"));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals("[{\"value\":\"d1\"},{\"value\":\"d2\"}]", httpResponse.body());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPut httpRequest = new HttpPut("/put/123");
            httpRequest.setEntity(new StringEntity(GSonUtils.encode(BodyContent.TYPE.instantiate()
                    .set(BodyContent.DATA, ""), false
            )));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpDelete httpRequest = new HttpDelete("/delete/123");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpOptions httpRequest = new HttpOptions("/post");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHead httpRequest = new HttpHead("/post");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(403, httpResponse.code());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPatch httpRequest = new HttpPatch("/test-custom-status-code");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(201, httpResponse.code());
            Assert.assertEquals("{\"DATA\":\"custom data works\"}", httpResponse.body());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            //check longer query.
            HttpGet httpRequest = new HttpGet("/query/with/additional/unexpectedPath");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(403, httpResponse.code());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            //check longer query.
            HttpGet httpRequest = new HttpGet("/path/with/additional/expected");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals("{\"DATA\":\"Get with with,additional,expected\"}", httpResponse.body());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            //check longer query.
            HttpGet httpRequest = new HttpGet("/path/with");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals("{\"DATA\":\"Get with with\"}", httpResponse.body());
        }
//        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
//            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
//            multipartEntityBuilder.addBinaryBody("First", "some Data".getBytes(StandardCharsets.UTF_8));
//            multipartEntityBuilder.addBinaryBody("Second", "some other data".getBytes(StandardCharsets.UTF_8));
//            HttpPost httpRequest = new HttpPost("/binaryCall");
//            httpRequest.setEntity(multipartEntityBuilder.build());
//            CloseableHttpResponse httpResponse = httpclient.execute(target, httpRequest);
//            Assert.assertEquals(200, httpResponse.getCode());
//            String str = new String(httpResponse.getEntity().getContent().readAllBytes());
//            Assert.assertEquals("some Data", str);
//        }
    }

    @Test
    public void EmptyBodyInPostIsNull() throws IOException {
        String charsetName = "UTF-16";

        httpServerRegister.register("/send", null)
                .post(BodyContent.TYPE, null, (body, url, queryParameters) -> {
                    if (body != null) {
                        throw new IllegalArgumentException("body must be null");
                    }
                    return CompletableFuture.completedFuture(body);
                        }
                );
        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("http", "localhost", port);

            HttpPost httpPost = new HttpPost("/send");
            httpPost.setEntity(new StringEntity("", ContentType.APPLICATION_JSON));
            Resp httpResponse = execute(httpclient, target, httpPost);
            Assert.assertEquals(204, httpResponse.code());
        }
    }

    @Test
    public void testGlobHttpContent() throws IOException, ParseException {
        String charsetName = "UTF-16";
        byte[] bytes = "coucou".getBytes(charsetName);
        Glob glob = GlobHttpContent.TYPE.instantiate()
                .set(GlobHttpContent.mimeType, "text/plain")
                .set(GlobHttpContent.charset, charsetName)
                .set(GlobHttpContent.content, bytes);

        httpServerRegister.register("/send", null)
                .post(GlobHttpContent.TYPE, null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(null)
                );
        httpServerRegister.register("/receive", null)
                .get(null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(glob)
                ).declareReturnType(GlobHttpContent.TYPE);

        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("http", "localhost", port);

            {
            HttpPost httpPost = new HttpPost("/send");
            httpPost.setEntity(new StringEntity(GSonUtils.encode(glob, false), ContentType.APPLICATION_JSON));
            Resp httpResponse = execute(httpclient, target, httpPost);
            Assert.assertEquals(204, httpResponse.code());
            }

            {
                HttpGet httpGet = new HttpGet("/receive");
                Resp httpResponse = execute(httpclient, target, httpGet);
                Assert.assertEquals(200, httpResponse.code());
                Assert.assertEquals("text/plain; charset=" + charsetName, httpResponse.contentType());
                Assert.assertEquals(14, httpResponse.contentLength());
                Assert.assertEquals("coucou", httpResponse.body());
            }
        }
    }

    @Test
    @Ignore
    public void testGlobFile() throws IOException, InterruptedException, ParseException {
        File sentFile = File.createTempFile("httpContent", ".json");
        sentFile.deleteOnExit();
        Files.writeString(sentFile.toPath(), "file data sent");
        String sentFileAbsolutePath = sentFile.getAbsolutePath();

        File receivedFile = File.createTempFile("httpContent", ".json");
        receivedFile.deleteOnExit();
        Files.writeString(receivedFile.toPath(), "file data received");
        String receivedFileAbsolutePath = receivedFile.getAbsolutePath();

        httpServerRegister.register("/send", null)
                .post(GlobFile.TYPE, null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(null)
                );
        httpServerRegister.register("/receive", null)
                .get(null, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(GlobFile.TYPE.instantiate()
                                .set(GlobFile.mimeType, "text/plain")
                                .set(GlobFile.file, receivedFileAbsolutePath)
                                .set(GlobFile.removeWhenDelivered, true))
                ).declareReturnType(GlobHttpContent.TYPE);

        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("http", "localhost", port);

            HttpPost httpPost = new HttpPost("/send");
            httpPost.setEntity(new StringEntity(GSonUtils.encode(GlobFile.TYPE.instantiate()
                    .set(GlobFile.mimeType, "text/plain")
                    .set(GlobFile.file, sentFileAbsolutePath)
                    .set(GlobFile.removeWhenDelivered, true), false), ContentType.APPLICATION_JSON));
            Resp httpResponse = execute(httpclient, target, httpPost);
            Assert.assertEquals(204, httpResponse.code());

            HttpGet httpGet = new HttpGet("/receive");
            httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals("text/plain; charset=UTF-8", httpResponse.contentType());
            Assert.assertEquals("file data received", httpResponse.body());
        }
    }

    @Test
    public void testThrowable() throws IOException, InterruptedException, ParseException {
        httpServerRegister.register("/hello", null)
                .get(QueryParameter2.TYPE, (body, url, queryParameters) -> {
                    String value = queryParameters.get(QueryParameter2.value);
                    if (value == null) {
                        throw new HttpException(400, "missing parameter value");
                    } else if ("Cow".equalsIgnoreCase(value)) {
                        throw new HttpExceptionWithContent(405, Response1.TYPE.instantiate()
                                .set(Response1.value, "no animal allowed")
                        );
                    } else if ("John".equalsIgnoreCase(value)) {
                        throw new HttpException(403, "banned");
                    } else if ("Superman".equals(value)) {
                        return CompletableFuture.failedFuture(new HttpException(408, "too strong"));
                    } else if ("Batman".equals(value)) {
                        throw new IllegalArgumentException("system error");
                    }

                    return CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                            .set(Response1.value, "welcome " + value));
                }).declareReturnType(GlobHttpContent.TYPE);

        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("http", "localhost", port);

            HttpGet httpGet = new HttpGet("/hello?value=Marc");
            Resp httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "welcome Marc"), GSonUtils.decode(httpResponse.body(), Response1.TYPE));

            httpGet = new HttpGet("/hello?value=Cow");
            httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(405, httpResponse.code());
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "no animal allowed"), GSonUtils.decode(httpResponse.body(), Response1.TYPE));

            httpGet = new HttpGet("/hello?value=John");
            httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(403, httpResponse.code());
            Assert.assertEquals("", httpResponse.body());
            Assert.assertEquals("banned", httpResponse.reasonPhrase());

            httpGet = new HttpGet("/hello?value=Superman");
            httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(408, httpResponse.code());
            Assert.assertEquals("", httpResponse.body());
            Assert.assertEquals("too strong", httpResponse.reasonPhrase());

            httpGet = new HttpGet("/hello?value=Batman");
            httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(500, httpResponse.code());
            Assert.assertEquals("", httpResponse.body());
            Assert.assertEquals("Internal Server Error", httpResponse.reasonPhrase());
        }
    }

    @Test
    @Ignore
    public void testCompressed() throws IOException, InterruptedException, ParseException {
        httpServerRegister.register("/query", null)
                .get(QueryParameter2.TYPE, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                                .set(Response1.value, queryParameters.get(QueryParameter2.value))
                        )
                );

        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("http", "localhost", port);

            HttpGet httpGet = GlobHttpUtils.createGet("/query", QueryParameter2.TYPE.instantiate()
                    .set(QueryParameter2.value, "uncompressed"));
            httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, "none");
            Resp httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertFalse(DecompressingEntity.class.isAssignableFrom(httpResponse.entityClass()));
            Assert.assertEquals("{\"value\":\"uncompressed\"}", httpResponse.body());
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "uncompressed"), GSonUtils.decode(httpResponse.body(), Response1.TYPE));

            httpGet = GlobHttpUtils.createGet("/query", QueryParameter2.TYPE.instantiate()
                    .set(QueryParameter2.value, "compressed"));
            httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
            httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(200, httpResponse.code());
            Assert.assertTrue(DecompressingEntity.class.isAssignableFrom(httpResponse.entityClass()));
            Assert.assertEquals("{\"value\":\"compressed\"}", httpResponse.body());
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "compressed"), GSonUtils.decode(httpResponse.body(), Response1.TYPE));
        }
    }

    @Test
    public void openApiScope() throws IOException, InterruptedException {
        httpServerRegister.register("/test", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> null);

        httpServerRegister.register("/test/{id}", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> null)
                .declareTags(new String[]{"test-scope"});

        httpServerRegister.registerOpenApi(globOpenApi);

        startServer();

        String encode = GSonUtils.encode(globOpenApi.getOpenApiDoc(), false);
        System.out.println(encode);

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("http", "localhost", port);

            HttpGet httpGet = GlobHttpUtils.createGet("/api?" + GetOpenApiParamType.scope.getName() + "=test-scope", null);
            Resp httpResponse = execute(httpclient, target, httpGet);
            Assert.assertEquals(200, httpResponse.code());
            String body = httpResponse.body();
            String expectedBody = "{\"openapi\":\"3.0.1\",\"info\":{\"title\":\"TestServer/1.1\",\"description\":\"TestServer/1.1\",\"version\":\"1.0\"},\"components\":{\"schemas\":{\"queryParameter\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"info\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"param\":{\"$ref\":\"#/components/schemas/queryParameter\"}}}}},\"servers\":[{\"url\":\"http://localhost:" + port + "\"}],\"paths\":{\"/test/{id}\":{\"get\":{\"tags\":[\"test-scope\"],\"parameters\":[{\"in\":\"path\",\"name\":\"id\",\"required\":true,\"schema\":{\"type\":\"integer\",\"format\":\"int64\"}},{\"in\":\"query\",\"name\":\"name\",\"required\":true,\"schema\":{\"type\":\"string\"}},{\"in\":\"query\",\"name\":\"info\",\"required\":true,\"schema\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},{\"in\":\"query\",\"name\":\"param\",\"required\":true,\"schema\":{\"$ref\":\"#/components/schemas/queryParameter\"}}],\"responses\":{\"200\":{\"description\":\"None\"}}}}}}";
            Assert.assertEquals(expectedBody, body);

            // TODO: the field gets correctly serialized but we do not seem able to deserialize it correctly
            Glob decodedBody = GSonUtils.decode(body, OpenApiType.TYPE);
            System.out.println("received body");
            System.out.println(body);
            System.out.println("decoded body");
            System.out.println(decodedBody);
            Assert.assertNotNull(decodedBody);
//            Assert.assertEquals(decodedBody.getOrEmpty(OpenApiType.paths).length, 1);
        }
    }

    @Test
    public void multiValueStringQueryParam() throws IOException, InterruptedException {
        httpServerRegister.register("/multi/{id}", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    return null;
                });

        startServer();

        HttpHost target = new HttpHost("http", "localhost", port);

        // a repeated param and a comma separated one both feed the same StringArrayField
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/multi/123?name=ZERZE&info=A&info=B%2CC");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
            Pair<Glob, Glob> poll = pairs.poll(2, TimeUnit.SECONDS);
            Assert.assertNotNull(poll);
            Assert.assertEquals(123, poll.getFirst().get(URLOneParameter.ID, 0));
            Assert.assertEquals("ZERZE", poll.getSecond().get(QueryParameter.NAME));
            Assert.assertArrayEquals(new String[]{"A", "B", "C"}, poll.getSecond().get(QueryParameter.INFO));
        }

        // a single value gives an array of one element
        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = GlobHttpUtils.createGet("/multi/123", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.INFO, new String[]{"only one"}));
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
            Pair<Glob, Glob> poll = pairs.poll(2, TimeUnit.SECONDS);
            Assert.assertNotNull(poll);
            Assert.assertArrayEquals(new String[]{"only one"}, poll.getSecond().get(QueryParameter.INFO));
        }

        // an absent param leaves the field null
        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/multi/123?name=ZERZE");
            Resp httpResponse = execute(httpclient, target, httpRequest);
            Assert.assertEquals(204, httpResponse.code());
            Pair<Glob, Glob> poll = pairs.poll(2, TimeUnit.SECONDS);
            Assert.assertNotNull(poll);
            Assert.assertNull(poll.getSecond().get(QueryParameter.INFO));
        }
    }

    @Test
    public void xmlInOut() throws IOException {
        File httpContent = File.createTempFile("httpContent", ".xml");
        httpContent.deleteOnExit();
        Files.writeString(httpContent.toPath(), "[]");

        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    return null;
                });

        startServer();
    }

    /**
     * The content of a response, read inside the response handler : the entity is not usable anymore
     * once {@link CloseableHttpClient#execute(HttpHost, ClassicHttpRequest, HttpClientResponseHandler)} returned.
     */
    private record Resp(int code, String reasonPhrase, String contentType, long contentLength,
                        Class<?> entityClass, String body) {
    }

    private static Resp execute(CloseableHttpClient httpclient, HttpHost target, ClassicHttpRequest request) throws IOException {
        return httpclient.execute(target, request, response -> {
            HttpEntity entity = response.getEntity();
            return new Resp(response.getCode(), response.getReasonPhrase(),
                    entity != null ? entity.getContentType() : null,
                    entity != null ? entity.getContentLength() : -1,
                    entity != null ? entity.getClass() : null,
                    entity != null ? EntityUtils.toString(entity) : "");
        });
    }

    private void startServer() {
        GlobHttpApacheBuilder globHttpApacheBuilder = new GlobHttpApacheBuilder(httpServerRegister);
        Server serverInstance = globHttpApacheBuilder.startAndWaitForStartup(bootstrap, 0);
        server = serverInstance.getServer();
        port = serverInstance.getPort();
        this.globOpenApi.initOpenApiDoc(port);
        System.out.println("port:" + port);
    }

    static public class URLParameter {
        public static GlobType TYPE;

        @FieldName_("id")
        public static LongField ID;

        @FieldName_("subId")
        public static LongField SUBID;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("URLParameter");
            ID = typeBuilder.declareLongField("id");
            SUBID = typeBuilder.declareLongField("subId");
            TYPE = typeBuilder.build();
        }
    }

    static public class URLWithArray {
        public static GlobType TYPE;

        public static StringArrayField path;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("URLWithArray");
            path = typeBuilder.declareStringArrayField("path");
            TYPE = typeBuilder.build();
        }
    }

    static public class URLOneParameter {
        public static GlobType TYPE;

        @FieldName_("id")
        public static LongField ID;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("URLOneParameter");
            ID = typeBuilder.declareLongField("id");
            TYPE = typeBuilder.build();
        }
    }

    static public class HeaderType {
        public static GlobType TYPE;

        @FieldName_("X-Glob-http-ID")
        public static StringField name;

        public static StringField id;

        public static StringField token;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("HeaderType");
            name = typeBuilder.declareStringField("X-Glob-http-ID");
            id = typeBuilder.declareStringField("id");
            token = typeBuilder.declareStringField("token");
            TYPE = typeBuilder.build();
        }
    }

    static public class QueryParameter {
        public static GlobType TYPE;

        // add mendatory
        public static StringField NAME;

        public static StringArrayField INFO;

        @Target(QueryParameter.class)
        public static GlobField param;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("queryParameter");
            NAME = typeBuilder.declareStringField("name");
            INFO = typeBuilder.declareStringArrayField("info");
            param = typeBuilder.declareGlobField("param", () -> QueryParameter.TYPE);
            TYPE = typeBuilder.build();
        }
    }

    static public class QueryParameter2 {
        public static GlobType TYPE;

        public static StringField value;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("queryParameter2");
            value = typeBuilder.declareStringField("value");
            TYPE = typeBuilder.build();
        }
    }

    static public class BodyContent {
        public static GlobType TYPE;

        public static StringField DATA;

        @Targets({U1.class, U2.class})
        public static GlobUnionField testUnion;

        @Targets({U1.class, U2.class})
        public static GlobArrayUnionField testUnions;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("BodyContent");
            DATA = typeBuilder.declareStringField("DATA");
            testUnion = typeBuilder.declareGlobUnionField("testUnion", new Supplier[]{() -> U1.TYPE, () -> U2.TYPE});
            testUnions = typeBuilder.declareGlobUnionArrayField("testUnions", new Supplier[]{() -> U1.TYPE, () -> U2.TYPE});
            TYPE = typeBuilder.build();
        }
    }

    static public class CustomBodyWithStatusCode {
        @HttpGlobResponse_
        public static GlobType TYPE;

        @StatusCode_
        public static IntegerField field1;

        @Target(BodyContent.class)
        @HttpBodyData_
        public static GlobField field2;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("CustomBodyWithStatusCode");
            typeBuilder.addAnnotation(HttpGlobResponse.UNIQUE_INSTANCE);
            field1 = typeBuilder.declareIntegerField("field1", StatusCode.UNIQUE_INSTANCE);
            field2 = typeBuilder.declareGlobField("field2", () -> BodyContent.TYPE, HttpBodyData.UNIQUE_INSTANCE);
            TYPE = typeBuilder.build();
        }
    }

    static public class Response1 {
        public static GlobType TYPE;

        @KeyField_
        public static StringField value;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("Response1");
            value = typeBuilder.declareStringField("value", KeyField.ZERO);
            TYPE = typeBuilder.build();
        }
    }

    static public class ResponseWithSensibleData {
        public static GlobType TYPE;

        @KeyField_
        @JsonHideValue_
        public static StringField field1;

        @KeyField_
        public static StringField field2;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("ResponseWithSensibleData");
            field1 = typeBuilder.declareStringField("field1", KeyField.ZERO, JsonHideValue.UNIQUE_GLOB);
            field2 = typeBuilder.declareStringField("field2", KeyField.ONE);
            TYPE = typeBuilder.build();
        }
    }

    static public class U1 {
        public static GlobType TYPE;

        @KeyField_
        public static StringField someValue;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("U1");
            someValue = typeBuilder.declareStringField("someValue", KeyField.ZERO);
            TYPE = typeBuilder.build();
        }
    }

    static public class U2 {
        public static GlobType TYPE;

        @KeyField_
        public static StringField someOtherValue;

        static {
            GlobTypeBuilder typeBuilder = new DefaultGlobTypeBuilder("U2");
            someOtherValue = typeBuilder.declareStringField("someOtherValue", KeyField.ZERO);
            TYPE = typeBuilder.build();
        }
    }
}





