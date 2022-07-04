package org.globsframework.http;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
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
import org.globsframework.http.model.Data;
import org.globsframework.http.model.StatusCode;
import org.globsframework.http.openapi.model.GetOpenApiParamType;
import org.globsframework.http.openapi.model.OpenApiType;
import org.globsframework.json.GSonUtils;
import org.globsframework.json.annottations.JsonHidValue_;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.annotations.Targets;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.Glob;
import org.globsframework.utils.Files;
import org.globsframework.utils.collections.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

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

        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    activeId[0] = "/test/{id}/TOTO/{subId}";
                    return null;
                });

        httpServerRegister.register("/test/{id}", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    activeId[0] = "/test/{id}";
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

        httpServerRegister.register("/query", null)
                .get(null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(null);
                });

        httpServerRegister.register("/query", null)
                .post(BodyContent.TYPE, null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                            .set(BodyContent.DATA, "some important information."));
                })
                .declareReturnType(BodyContent.TYPE);

        httpServerRegister.register("/query", null)
                .put(BodyContent.TYPE, null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                            .set(BodyContent.DATA, "some important information."));
                })
                .declareReturnType(BodyContent.TYPE);

        httpServerRegister.register("/delete/{id}", URLOneParameter.TYPE)
                .delete(null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                            .set(BodyContent.DATA, "some important information."));
                })
                .declareReturnType(BodyContent.TYPE);

        httpServerRegister.register("/path/{path}", URLWithArray.TYPE)
                .get(null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                            .set(BodyContent.DATA, "Get with " + String.join(",", url.get(URLWithArray.path))));
                })
                .declareReturnType(BodyContent.TYPE);

        httpServerRegister.register("/test-custom-status-code", null)
                .patch(BodyContent.TYPE, null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(CustomBodyWithStatusCode.TYPE.instantiate()
                            .set(CustomBodyWithStatusCode.field1, 201)
                            .set(CustomBodyWithStatusCode.field2, BodyContent.TYPE.instantiate()
                                    .set(BodyContent.DATA, "custom data works")
                            )
                    );
                })
                .declareReturnType(BodyContent.TYPE);

        startServer();

        Glob openApiDoc = httpServerRegister.createOpenApiDoc(port);
        String encode = GSonUtils.encode(openApiDoc, false);
        System.out.println(encode);

        HttpClient httpclient = HttpClients.createDefault();

        HttpHost target = new HttpHost("localhost", port, "http");

        {
            HttpGet httpGet = GlobHttpUtils.createGet("/test/123/TOTO/4567", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            HttpResponse httpResponse = httpclient.execute(target, httpGet);
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

        {
            HttpGet httpGet = GlobHttpUtils.createGet("/test/123/TOTO", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            HttpResponse httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(204, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("/test/{id}/TOTO", activeId[0]);
        }

        {
            HttpGet httpGet = GlobHttpUtils.createGet("/test/123", QueryParameter.TYPE.instantiate()
                    .set(QueryParameter.NAME, "ZERZE").set(QueryParameter.INFO, new String[]{"A", "B", "C", "D"})
                    .set(QueryParameter.param, QueryParameter.TYPE.instantiate().set(QueryParameter.NAME, "AAAZZZ")));
            HttpResponse httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("/test/{id}", activeId[0]);
        }

        {
            HttpGet httpGetFile = new HttpGet("/query");
            HttpResponse httpFileResponse = httpclient.execute(target, httpGetFile);
            Assert.assertEquals(204, httpFileResponse.getStatusLine().getStatusCode());
            EntityUtils.consume(httpFileResponse.getEntity());
        }

        {
            HttpPost httpPostFile = new HttpPost("/query");
            HttpResponse httpPostResponse = httpclient.execute(target, httpPostFile);
            Assert.assertEquals(200, httpPostResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"some important information.\"}", Files.loadStreamToString(httpPostResponse.getEntity().getContent(), "UTF-8"));
        }

        {
            HttpPut httpPut = new HttpPut("/query");
            HttpResponse httpResponse = httpclient.execute(target, httpPut);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"some important information.\"}", Files.loadStreamToString(httpResponse.getEntity().getContent(), "UTF-8"));
        }

        {
            HttpDelete httpDelete = new HttpDelete("/delete/123");
            HttpResponse httpDeleteResponse = httpclient.execute(target, httpDelete);
            Assert.assertEquals(200, httpDeleteResponse.getStatusLine().getStatusCode());
            EntityUtils.consume(httpDeleteResponse.getEntity());
        }

        {
            HttpOptions httpOptions = new HttpOptions("/query");
            HttpResponse httpResponse = httpclient.execute(target, httpOptions);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        }

        {
            HttpHead httpHead = new HttpHead("/query");
            HttpResponse httpResponse = httpclient.execute(target, httpHead);
            Assert.assertEquals(403, httpResponse.getStatusLine().getStatusCode());
        }

        {
            HttpPatch httpPatch = new HttpPatch("/test-custom-status-code");
            HttpResponse httpPostResponse = httpclient.execute(target, httpPatch);
            Assert.assertEquals(201, httpPostResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"custom data works\"}", Files.loadStreamToString(httpPostResponse.getEntity().getContent(), "UTF-8"));
        }

        {
            //check longer query.
            HttpGet httpGetFile = new HttpGet("/query/with/additional/unexpectedPath");
            HttpResponse httpFileResponse = httpclient.execute(target, httpGetFile);
            Assert.assertEquals(403, httpFileResponse.getStatusLine().getStatusCode());
        }

        {
            //check longer query.
            HttpGet httpGetFile = new HttpGet("/path/with/additional/expected");
            HttpResponse httpFileResponse = httpclient.execute(target, httpGetFile);
            Assert.assertEquals(200, httpFileResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"Get with with,additional,expected\"}", Files.loadStreamToString(httpFileResponse.getEntity().getContent(), "UTF-8"));
        }

        {
            //check longer query.
            HttpGet httpGetFile = new HttpGet("/path/with");
            HttpResponse httpFileResponse = httpclient.execute(target, httpGetFile);
            Assert.assertEquals(200, httpFileResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"Get with with\"}", Files.loadStreamToString(httpFileResponse.getEntity().getContent(), "UTF-8"));
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
            Assert.assertEquals("coucou", EntityUtils.toString(httpResponse.getEntity(), charsetName));
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
            Assert.assertEquals("file data received", EntityUtils.toString(httpResponse.getEntity(), "UTF-8"));
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
            String strContent = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "welcome Marc"), GSonUtils.decode(strContent, Response1.TYPE));

            httpGet = new HttpGet("/hello?value=Cow");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(405, httpResponse.getStatusLine().getStatusCode());
            strContent = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "no animal allowed"), GSonUtils.decode(strContent, Response1.TYPE));

            httpGet = new HttpGet("/hello?value=John");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(403, httpResponse.getStatusLine().getStatusCode());
            strContent = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            Assert.assertEquals("", strContent);
            Assert.assertEquals("banned", httpResponse.getStatusLine().getReasonPhrase());

            httpGet = new HttpGet("/hello?value=Batman");
            httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(500, httpResponse.getStatusLine().getStatusCode());
            strContent = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            Assert.assertEquals("", strContent);
            Assert.assertEquals("Internal Server Error", httpResponse.getStatusLine().getReasonPhrase());
        }
    }

    @Test
    public void testCompressed() throws IOException, InterruptedException {
        httpServerRegister.register("/uncompressed", null)
                .get(QueryParameter2.TYPE, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                            .set(Response1.value, "uncompressed")
                    );
                });
        httpServerRegister.register("/compressed", null)
                .setGzipCompress()
                .get(QueryParameter2.TYPE, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                            .set(Response1.value, "compressed")
                    );
                });

        startServer();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("localhost", port, "http");

            HttpGet httpGet = GlobHttpUtils.createGet("/uncompressed", QueryParameter2.TYPE.instantiate()
                    .set(QueryParameter2.value, "toto"));

            HttpResponse httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());

            String strContent = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
            Assert.assertEquals("{\"value\":\"uncompressed\"}", strContent);
            Assert.assertEquals(Response1.TYPE.instantiate()
                    .set(Response1.value, "compressed"), GSonUtils.decode(strContent, Response1.TYPE));
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpHost target = new HttpHost("localhost", port, "http");

            HttpGet httpGet = GlobHttpUtils.createGet("/compressed", QueryParameter2.TYPE.instantiate()
                    .set(QueryParameter2.value, "toto"));

            HttpResponse httpResponse = httpclient.execute(target, httpGet);
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());

            String strContent = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
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
            String expectedBody = "{\"openapi\":\"3.0.1\",\"info\":{\"title\":\"TestServer/1.1\",\"description\":\"TestServer/1.1\",\"version\":\"1.0\"},\"components\":{\"schemas\":{\"queryParameter\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"info\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"param\":{\"$ref\":\"#/components/schemas/queryParameter\"}}},\"openApiType\":{\"type\":\"object\",\"properties\":{\"openapi\":{\"type\":\"string\"},\"info\":{\"$ref\":\"#/components/schemas/openApiInfo\"},\"components\":{\"$ref\":\"#/components/schemas/openApiComponents\"},\"servers\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiServers\"}},\"paths\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiPath\"}}}},\"openApiInfo\":{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"version\":{\"type\":\"string\"}}},\"openApiComponents\":{\"type\":\"object\",\"properties\":{\"schemas\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiSchemaProperty\"}}}},\"openApiSchemaProperty\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"type\":{\"type\":\"string\"},\"anyOf\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiSchemaProperty\"}},\"format\":{\"type\":\"string\"},\"minimum\":{\"type\":\"integer\",\"format\":\"int32\"},\"maximum\":{\"type\":\"integer\",\"format\":\"int32\"},\"properties\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiSchemaProperty\"}},\"items\":{\"$ref\":\"#/components/schemas/openApiSchemaProperty\"},\"$ref\":{\"type\":\"string\"}}},\"openApiServers\":{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"}}},\"openApiPath\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"put\":{\"$ref\":\"#/components/schemas/openApiPathDsc\"},\"post\":{\"$ref\":\"#/components/schemas/openApiPathDsc\"},\"patch\":{\"$ref\":\"#/components/schemas/openApiPathDsc\"},\"get\":{\"$ref\":\"#/components/schemas/openApiPathDsc\"},\"delete\":{\"$ref\":\"#/components/schemas/openApiPathDsc\"}}},\"openApiPathDsc\":{\"type\":\"object\",\"properties\":{\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"summary\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"operationId\":{\"type\":\"string\"},\"parameters\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiParameter\"}},\"requestBody\":{\"$ref\":\"#/components/schemas/openApiRequestBody\"},\"responses\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiResponses\"}}}},\"openApiParameter\":{\"type\":\"object\",\"properties\":{\"in\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"required\":{\"type\":\"boolean\"},\"schema\":{\"$ref\":\"#/components/schemas/openApiSchemaProperty\"}}},\"openApiRequestBody\":{\"type\":\"object\",\"properties\":{\"description\":{\"type\":\"string\"},\"required\":{\"type\":\"boolean\"},\"content\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiBodyMimeType\"}}}},\"openApiBodyMimeType\":{\"type\":\"object\",\"properties\":{\"mimeType\":{\"type\":\"string\"},\"schema\":{\"$ref\":\"#/components/schemas/openApiSchemaProperty\"}}},\"openApiResponses\":{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"content\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/openApiBodyMimeType\"}}}}}},\"servers\":[{\"url\":\"http://localhost:" + port + "\"}],\"paths\":{\"/test/{id}\":{\"get\":{\"tags\":[\"test-scope\"],\"parameters\":[{\"in\":\"path\",\"name\":\"id\",\"required\":true,\"schema\":{\"type\":\"integer\",\"format\":\"int64\"}},{\"in\":\"query\",\"name\":\"name\",\"required\":true,\"schema\":{\"type\":\"string\"}},{\"in\":\"query\",\"name\":\"info\",\"required\":true,\"schema\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},{\"in\":\"query\",\"name\":\"param\",\"required\":true,\"schema\":{\"$ref\":\"#/components/schemas/queryParameter\"}}],\"responses\":{\"200\":{\"description\":\"None\"}}}}}}";
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
        Pair<HttpServer, Integer> httpServerIntegerPair = httpServerRegister.startAndWaitForStartup(bootstrap);
        server = httpServerIntegerPair.getFirst();
        port = httpServerIntegerPair.getSecond();
        System.out.println("port:" + port);
    }

    static public class URLParameter {
        public static GlobType TYPE;

        @FieldNameAnnotation("id")
        public static LongField ID;

        @FieldNameAnnotation("subId")
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

        @FieldNameAnnotation("id")
        public static LongField ID;

        static {
            GlobTypeLoaderFactory.create(URLOneParameter.class, true).load();
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

        @StatusCode
        public static IntegerField field1;

        @Target(BodyContent.class)
        @Data
        public static GlobField field2;

        static {
            GlobTypeLoaderFactory.create(CustomBodyWithStatusCode.class).load();
        }
    }

    static public class Response1 {
        public static GlobType TYPE;

        public static StringField value;

        static {
            GlobTypeLoaderFactory.create(Response1.class).load();
        }
    }

    static public class ResponseWithSensibleData {
        public static GlobType TYPE;

        @JsonHidValue_
        public static StringField field1;

        public static StringField field2;

        static {
            GlobTypeLoaderFactory.create(ResponseWithSensibleData.class).load();
        }
    }

    static public class U1 {
        public static GlobType TYPE;

        public static StringField someValue;

        static {
            GlobTypeLoaderFactory.create(U1.class).load();
        }
    }

    static public class U2 {
        public static GlobType TYPE;

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





