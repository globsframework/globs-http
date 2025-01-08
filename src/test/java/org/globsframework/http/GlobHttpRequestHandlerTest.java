package org.globsframework.http;

import org.apache.http.HttpException;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.DecompressingEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.annotations.*;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.utils.Files;
import org.globsframework.core.utils.Ref;
import org.globsframework.core.utils.collections.Pair;
import org.globsframework.http.model.HttpBodyData_;
import org.globsframework.http.model.StatusCode_;
import org.globsframework.http.openapi.model.GetOpenApiParamType;
import org.globsframework.http.openapi.model.OpenApiType;
import org.globsframework.json.GSonUtils;
import org.globsframework.json.annottations.JsonHideValue_;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class GlobHttpRequestHandlerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("test");

    private ServerBootstrap bootstrap;
    private HttpServer server;
    private int port;
    private HttpServerRegister httpServerRegister;

    private BlockingQueue<Pair<Glob, Glob>> pairs;

    @Before
    public void init() {
        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoReuseAddress(true)
                .setSoTimeout(15000)
                .setTcpNoDelay(true)
                .build();

        bootstrap = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setIOReactorConfig(config);

        httpServerRegister = new HttpServerRegister("TestServer/1.1");

        pairs = new LinkedBlockingDeque<>();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.shutdown(0, TimeUnit.MINUTES);
        }
    }

    @Test
    public void name() throws IOException, InterruptedException {
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

        httpServerRegister.register("/binaryCall", null)
                .postBin(null, null, (body, url, queryParameters, headerType) -> {
                    final InputStream inputStream = body.asStream();
                    return CompletableFuture.completedFuture(HttpOutputData.asStream(inputStream));
                });

        startServer();

        Glob openApiDoc = httpServerRegister.createOpenApiDoc(port);
        String encode = GSonUtils.encode(openApiDoc, false);
        System.out.println(encode);

        HttpHost target = new HttpHost("localhost", port, "http");

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = GlobHttpUtils.createGet("/test/123/TOTO/4567", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
            Pair<Glob, Glob> poll = pairs.poll(2, TimeUnit.SECONDS);
            Assert.assertNotNull(poll);
            Assert.assertEquals(123, poll.getFirst().get(URLParameter.ID, 0));
            Assert.assertEquals(4567, poll.getFirst().get(URLParameter.SUBID, 0));
            Assert.assertEquals("ZERZE", poll.getSecond().get(QueryParameter.NAME));
            Assert.assertArrayEquals(new String[]{"A", "B", "C", "D"}, poll.getSecond().get(QueryParameter.INFO));
            Assert.assertEquals("AAAZZZ", poll.getSecond().get(QueryParameter.param).get(QueryParameter.NAME));
            Assert.assertEquals("/test/{id}/TOTO/{subId}", activeId[0]);
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = GlobHttpUtils.createGet("/test/123/TOTO", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("/test/{id}/TOTO", activeId[0]);
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/test/123/TOTO?name=-10%25%20sur%20les%20articles%20de%20la%20saison%20hiver%202022");
//                    GlobHttpUtils.createGet("/test/123/TOTO", QueryParameter.TYPE.instantiate()
//                    .set(QueryParameter.NAME, "-10%%20sur%20les%20articles%20de%20la%20saison%20hiver%202022"));
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
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
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("/test/{id}", activeId[0]);
            Assert.assertNotNull(headers.get());
            Assert.assertEquals("my header", headers.get().get(HeaderType.name));
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpRequest = new HttpGet("/query/123");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"value\":\"some important information.\"}", EntityUtils.toString(httpResponse.getEntity()));
        }

        pairs.clear();
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpRequest = new HttpPost("/post");
            httpRequest.setEntity(new StringEntity(GSonUtils.encode(BodyContent.TYPE.instantiate()
                    .set(BodyContent.DATA, ""), false
            )));
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpRequest = new HttpPost("/post");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpRequest = new HttpPost("/binaryCall");
            httpRequest.setEntity(new StringEntity("Some data send"));
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            String str = new String(httpResponse.getEntity().getContent().readAllBytes());
            Assert.assertEquals("Some data send", str);
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPut httpRequest = new HttpPut("/put/123");
            httpRequest.setEntity(new StringEntity(GSonUtils.encode(BodyContent.TYPE.instantiate()
                    .set(BodyContent.DATA, ""), false
            )));
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpDelete httpRequest = new HttpDelete("/delete/123");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            EntityUtils.consume(httpResponse.getEntity());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpOptions httpRequest = new HttpOptions("/post");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHead httpRequest = new HttpHead("/post");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(403, httpResponse.getStatusLine().getStatusCode());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPatch httpRequest = new HttpPatch("/test-custom-status-code");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(201, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"custom data works\"}", EntityUtils.toString(httpResponse.getEntity()));
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            //check longer query.
            HttpGet httpRequest = new HttpGet("/query/with/additional/unexpectedPath");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(403, httpResponse.getStatusLine().getStatusCode());
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            //check longer query.
            HttpGet httpRequest = new HttpGet("/path/with/additional/expected");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"Get with with,additional,expected\"}", Files.loadStreamToString(httpResponse.getEntity().getContent(), "UTF-8"));
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            //check longer query.
            HttpGet httpRequest = new HttpGet("/path/with");
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"Get with with\"}", Files.loadStreamToString(httpResponse.getEntity().getContent(), "UTF-8"));
        }
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addBinaryBody("First", "some Data".getBytes(StandardCharsets.UTF_8));
            HttpPost httpRequest = new HttpPost("/binaryCall");
            httpRequest.setEntity(multipartEntityBuilder.build());
            HttpResponse httpResponse = httpclient.execute(target, httpRequest);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            String str = new String(httpResponse.getEntity().getContent().readAllBytes());
            Assert.assertEquals("some Data", str);
        }
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
            HttpHost target = new HttpHost("localhost", port, "http");

            HttpPost httpPost = new HttpPost("/send");
            httpPost.setEntity(new StringEntity("", ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpclient.execute(target, httpPost);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testGlobHttpContent() throws IOException, InterruptedException {
        String charsetName = "UTF-16";
        Glob glob = GlobHttpContent.TYPE.instantiate()
                .set(GlobHttpContent.mimeType, "text/plain")
                .set(GlobHttpContent.charset, charsetName)
                .set(GlobHttpContent.content, "coucou".getBytes(charsetName));

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
            HttpHost target = new HttpHost("localhost", port, "http");

            HttpPost httpPost = new HttpPost("/send");
            httpPost.setEntity(new StringEntity(GSonUtils.encode(glob, false), ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpclient.execute(target, httpPost);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());

            HttpGet httpGet = new HttpGet("/receive");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("text/plain; charset=" + charsetName, httpResponse.getEntity().getContentType().getValue());
            Assert.assertEquals("coucou", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testGlobFile() throws IOException, InterruptedException {
        File sentFile = File.createTempFile("httpContent", ".json");
        sentFile.deleteOnExit();
        Files.dumpStringToFile(sentFile, "file data sent");
        String sentFileAbsolutePath = sentFile.getAbsolutePath();

        File receivedFile = File.createTempFile("httpContent", ".json");
        receivedFile.deleteOnExit();
        Files.dumpStringToFile(receivedFile, "file data received");
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
            HttpHost target = new HttpHost("localhost", port, "http");

            HttpPost httpPost = new HttpPost("/send");
            httpPost.setEntity(new StringEntity(GSonUtils.encode(GlobFile.TYPE.instantiate()
                    .set(GlobFile.mimeType, "text/plain")
                    .set(GlobFile.file, sentFileAbsolutePath)
                    .set(GlobFile.removeWhenDelivered, true), false), ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpclient.execute(target, httpPost);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());

            HttpGet httpGet = new HttpGet("/receive");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("text/plain; charset=UTF-8", httpResponse.getEntity().getContentType().getValue());
            Assert.assertEquals("file data received", EntityUtils.toString(httpResponse.getEntity()));
        }
    }

    @Test
    public void testThrowable() throws IOException, InterruptedException {
        httpServerRegister.register("/hello", null)
                .get(QueryParameter2.TYPE, (body, url, queryParameters) -> {
                    String value = queryParameters.get(QueryParameter2.value);
                    if (value == null) {
                        throw new org.globsframework.http.HttpException(400, "missing parameter value");
                    } else if ("Cow".equalsIgnoreCase(value)) {
                        throw new org.globsframework.http.HttpExceptionWithContent(405, Response1.TYPE.instantiate()
                                .set(Response1.value, "no animal allowed")
                        );
                    } else if ("John".equalsIgnoreCase(value)) {
                        throw new org.globsframework.http.HttpException(403, "banned");
                    } else if ("Superman".equals(value)) {
                        return CompletableFuture.failedFuture(new org.globsframework.http.HttpException(408, "too strong"));
                    } else if ("Batman".equals(value)) {
                        throw new IllegalArgumentException("system error");
                    }

                    return CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                            .set(Response1.value, "welcome " + value));
                }).declareReturnType(GlobHttpContent.TYPE);

        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("localhost", port, "http");

            HttpGet httpGet = new HttpGet("/hello?value=Marc");
            HttpResponse httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            String strContent = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "welcome Marc"), GSonUtils.decode(strContent, Response1.TYPE));

            httpGet = new HttpGet("/hello?value=Cow");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(405, httpResponse.getStatusLine().getStatusCode());
            strContent = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "no animal allowed"), GSonUtils.decode(strContent, Response1.TYPE));

            httpGet = new HttpGet("/hello?value=John");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(403, httpResponse.getStatusLine().getStatusCode());
            strContent = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals("", strContent);
            Assert.assertEquals("banned", httpResponse.getStatusLine().getReasonPhrase());

            httpGet = new HttpGet("/hello?value=Superman");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(408, httpResponse.getStatusLine().getStatusCode());
            strContent = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals("", strContent);
            Assert.assertEquals("too strong", httpResponse.getStatusLine().getReasonPhrase());

            httpGet = new HttpGet("/hello?value=Batman");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(500, httpResponse.getStatusLine().getStatusCode());
            strContent = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals("", strContent);
            Assert.assertEquals("Internal Server Error", httpResponse.getStatusLine().getReasonPhrase());
        }
    }

    @Test
    public void testCompressed() throws IOException, InterruptedException {
        httpServerRegister.register("/query", null)
                .get(QueryParameter2.TYPE, (body, url, queryParameters) ->
                        CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                                .set(Response1.value, queryParameters.get(QueryParameter2.value))
                        )
                );

        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("localhost", port, "http");

            HttpGet httpGet = GlobHttpUtils.createGet("/query", QueryParameter2.TYPE.instantiate()
                    .set(QueryParameter2.value, "uncompressed"));
            httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, "none");
            HttpResponse httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertFalse(httpResponse.getEntity() instanceof DecompressingEntity);
            String strContent = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals("{\"value\":\"uncompressed\"}", strContent);
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "uncompressed"), GSonUtils.decode(strContent, Response1.TYPE));

            httpGet = GlobHttpUtils.createGet("/query", QueryParameter2.TYPE.instantiate()
                    .set(QueryParameter2.value, "compressed"));
            httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertTrue(httpResponse.getEntity() instanceof DecompressingEntity);
            strContent = EntityUtils.toString(httpResponse.getEntity());
            Assert.assertEquals("{\"value\":\"compressed\"}", strContent);
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "compressed"), GSonUtils.decode(strContent, Response1.TYPE));
        }
    }

    @Test
    public void openApiScope() throws IOException, InterruptedException {
        httpServerRegister.register("/test", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> null);

        httpServerRegister.register("/test/{id}", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> null)
                .declareTags(new String[]{"test-scope"});

        httpServerRegister.registerOpenApi();

        startServer();

        Glob openApiDoc = httpServerRegister.createOpenApiDoc(port);
        String encode = GSonUtils.encode(openApiDoc, false);
        System.out.println(encode);

        HttpClient httpclient = HttpClients.createDefault();

        HttpHost target = new HttpHost("localhost", port, "http");

        {
            HttpGet httpGet = GlobHttpUtils.createGet("/api?" + GetOpenApiParamType.scope.getName() + "=test-scope", null);
            HttpResponse httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            String body = Files.loadStreamToString(httpResponse.getEntity().getContent(), "UTF-8");
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
    public void xmlInOut() throws IOException {
        File httpContent = File.createTempFile("httpContent", ".xml");
        httpContent.deleteOnExit();
        Files.dumpStringToFile(httpContent, "[]");

        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    return null;
                });

        startServer();
    }

    private void startServer() {
        HttpServerRegister.HttpStartup httpServerIntegerPair = httpServerRegister.startAndWaitForStartup(bootstrap);
        server = httpServerIntegerPair.httpServer();
        port = httpServerIntegerPair.listenPort();
        System.out.println("port:" + port);
    }

    static public class URLParameter {
        public static GlobType TYPE;

        @FieldName_("id")
        public static LongField ID;

        @FieldName_("subId")
        public static LongField SUBID;

        static {
            GlobTypeLoaderFactory.create(URLParameter.class, true).load();
        }
    }

    static public class URLWithArray {
        public static GlobType TYPE;

        public static StringArrayField path;

        static {
            GlobTypeLoaderFactory.create(URLWithArray.class).load();
        }
    }

    static public class URLOneParameter {
        public static GlobType TYPE;

        @FieldName_("id")
        public static LongField ID;

        static {
            GlobTypeLoaderFactory.create(URLOneParameter.class, true).load();
        }
    }

    static public class HeaderType {
        public static GlobType TYPE;

        @FieldName_("X-Glob-http-ID")
        public static StringField name;

        public static StringField id;

        public static StringField token;

        static {
            GlobTypeLoaderFactory.create(HeaderType.class).load();
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
            GlobTypeLoaderFactory.create(QueryParameter.class, true).load();
        }
    }

    static public class QueryParameter2 {
        public static GlobType TYPE;

        public static StringField value;

        static {
            GlobTypeLoaderFactory.create(QueryParameter2.class, true).load();
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
            GlobTypeLoaderFactory.create(BodyContent.class).load();
        }
    }

    static public class CustomBodyWithStatusCode {
        public static GlobType TYPE;

        @StatusCode_
        public static IntegerField field1;

        @Target(BodyContent.class)
        @HttpBodyData_
        public static GlobField field2;

        static {
            GlobTypeLoaderFactory.create(CustomBodyWithStatusCode.class).load();
        }
    }

    static public class Response1 {
        public static GlobType TYPE;

        @KeyField_
        public static StringField value;

        static {
            GlobTypeLoaderFactory.create(Response1.class).load();
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
            GlobTypeLoaderFactory.create(ResponseWithSensibleData.class).load();
        }
    }

    static public class U1 {
        public static GlobType TYPE;

        @KeyField_
        public static StringField someValue;

        static {
            GlobTypeLoaderFactory.create(U1.class).load();
        }
    }

    static public class U2 {
        public static GlobType TYPE;

        @KeyField_
        public static StringField someOtherValue;

        static {
            GlobTypeLoaderFactory.create(U2.class).load();
        }
    }

    @Test
    public void standardBootstrapTest() throws IOException, InterruptedException {

        final String[] responder = {""};

        server = ServerBootstrap.bootstrap()
                //    .setHttpProcessor(getHttpProcessor())
                .registerHandler("/", new HttpAsyncRequestHandler<String>() {

                    @Override
                    public HttpAsyncRequestConsumer<String> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {
                        responder[0] = "/";
                        return null;
                    }

                    @Override
                    public void handle(String data, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {

                    }
                })
                .registerHandler("/withPath", new HttpAsyncRequestHandler<String>() {
                    @Override
                    public HttpAsyncRequestConsumer<String> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {

                        responder[0] = "/withPath";
                        LOGGER.info("In withPath consumer");
//                        return CompletableFuture.completedFuture("That looks good");
                        return null;
                    }

                    @Override
                    public void handle(String data, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
                        LOGGER.info("In withPath handler");
                    }
                })
                .registerHandler("/with/nested/path", new HttpAsyncRequestHandler<Object>() {
                    @Override
                    public HttpAsyncRequestConsumer<Object> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {

                        LOGGER.info("In /with/nested/path handler");
                        responder[0] = "/with/nested/path";
                        return null;
                    }

                    @Override
                    public void handle(Object data, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {

                    }
                })
                .registerHandler("/with/nested", new HttpAsyncRequestHandler<Object>() {
                    @Override
                    public HttpAsyncRequestConsumer<Object> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {
                        LOGGER.info("In /with/nested handler");
                        responder[0] = "/with/nested";
                        return null;
                    }

                    @Override
                    public void handle(Object data, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {

                    }
                })
                .create();

        server.start();
        server.getEndpoint().waitFor();
        InetSocketAddress address = (InetSocketAddress) server.getEndpoint().getAddress();
        Thread mainThread = new Thread(() -> {
            LOGGER.info("starting server");
            try {
                server.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                LOGGER.info("Interrupted");
            }
            LOGGER.info("Server started");

        });

        mainThread.start();

        String header = "http://localhost:" + address.getPort();

        HttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(header + "/with/nested");
        HttpResponse response = httpClient.execute(httpGet);
        Assert.assertEquals("/with/nested", responder[0]);


        httpClient = HttpClients.createDefault();
        httpGet = new HttpGet(header + "/with/nested/path");
        response = httpClient.execute(httpGet);
        Assert.assertEquals("/with/nested/path", responder[0]);

    }
}





