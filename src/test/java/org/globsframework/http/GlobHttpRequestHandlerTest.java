package org.globsframework.http;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.protocol.HttpAsyncExchange;
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer;
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.protocol.HttpContext;
import org.globsframework.http.model.Data;
import org.globsframework.http.model.StatusCode;
import org.globsframework.json.GSonUtils;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.metamodel.annotations.Target;
import org.globsframework.metamodel.annotations.Targets;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.Glob;
import org.globsframework.utils.Files;
import org.globsframework.utils.collections.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class GlobHttpRequestHandlerTest {

    private HttpServer server;
    Logger logger = LoggerFactory.getLogger("test");

    @Test
    public void name() throws IOException, InterruptedException {
        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoReuseAddress(true)
                .setSoTimeout(15000)
                .setTcpNoDelay(true)
                .build();

        ServerBootstrap bootstrap = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setIOReactorConfig(config);

        File httpContent = File.createTempFile("httpContent", ".json");
        httpContent.deleteOnExit();
        Files.dumpStringToFile(httpContent, "[]");
        String absolutePath = httpContent.getAbsolutePath();

        BlockingQueue<Pair<Glob, Glob>> pairs = new LinkedBlockingDeque<>();

        final String[] activeId = {""}; // Used to store an identifier of the request handler responding.


        HttpServerRegister httpServerRegister = new HttpServerRegister("PriceServer/1.1");
        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob url, Glob queryParameters) throws Exception {
                        pairs.add(Pair.makePair(url, queryParameters));
                        activeId[0] = "/test/{id}/TOTO/{subId}";
                        return null;
                    }
                });

        httpServerRegister.register("/test/{id}", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob url, Glob queryParameters) throws Exception {
                        pairs.add(Pair.makePair(url, queryParameters));
                        activeId[0] = "/test/{id}";
                        return null;
                    }
                });

        httpServerRegister.register("/test/{id}/TOTO", URLOneParameter.TYPE)
                .get(QueryParameter.TYPE, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob url, Glob queryParameters) throws Exception {
                        pairs.add(Pair.makePair(url, queryParameters));
                        activeId[0] = "/test/{id}/TOTO";
                        return null;
                    }
                });

        httpServerRegister.register("/query", null)
                .get(null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(GlobFile.TYPE.instantiate()
                            .set(GlobFile.file, absolutePath)
                            .set(GlobFile.removeWhenDelivered, true));
                });

        httpServerRegister.register("/query", null)
                //.setGzipCompress()
                .post(BodyContent.TYPE, null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                            .set(BodyContent.DATA, "some important information."));
                })
                .declareReturnType(BodyContent.TYPE);

        httpServerRegister.register("/path/{path}", URLWithArray.TYPE)
                .get( null, (body, url, queryParameters) -> {
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

        Pair<HttpServer, Integer> httpServerIntegerPair = httpServerRegister.startAndWaitForStartup(bootstrap);
        int port = httpServerIntegerPair.getSecond();
        server = httpServerIntegerPair.getFirst();
        System.out.println("port:" + port);

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
            Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
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
            Assert.assertEquals(200, httpFileResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("[]", Files.loadStreamToString(httpFileResponse.getEntity().getContent(), "UTF-8"));
        }

        {
            HttpPost httpPostFile = new HttpPost("/query");
            HttpResponse httpPostResponse = httpclient.execute(target, httpPostFile);
            Assert.assertEquals(200, httpPostResponse.getStatusLine().getStatusCode());
            Assert.assertEquals("{\"DATA\":\"some important information.\"}", Files.loadStreamToString(httpPostResponse.getEntity().getContent(), "UTF-8"));
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

        server.shutdown(0, TimeUnit.MINUTES);
        Assert.assertFalse(httpContent.exists());
        httpServerIntegerPair.getFirst().shutdown(0, TimeUnit.DAYS);
    }

    @Test
    public void xmlInOut() throws IOException {
        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoReuseAddress(true)
                .setSoTimeout(15000)
                .setTcpNoDelay(true)
                .build();

        ServerBootstrap bootstrap = ServerBootstrap.bootstrap()
                .setListenerPort(0)
                .setIOReactorConfig(config);

        File httpContent = File.createTempFile("httpContent", ".xml");
        httpContent.deleteOnExit();
        Files.dumpStringToFile(httpContent, "[]");
        String absolutePath = httpContent.getAbsolutePath();

        BlockingQueue<Pair<Glob, Glob>> pairs = new LinkedBlockingDeque<>();
        HttpServerRegister httpServerRegister = new HttpServerRegister("PriceServer/1.1");
        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob url, Glob queryParameters) throws Exception {
                        pairs.add(Pair.makePair(url, queryParameters));
                        return null;
                    }
                });

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

    static public class U1{
        public static GlobType TYPE;

        public static StringField someValue;
        static {
            GlobTypeLoaderFactory.create(U1.class).load();
        }
    }

    static public class U2{
        public static GlobType TYPE;

        public static StringField someOtherValue;
        static {
            GlobTypeLoaderFactory.create(U2.class).load();
        }
    }

    @Test
    public void standardBootstrapTest() throws IOException, InterruptedException {

        final String[] responder = {""};

        HttpServer localServer = ServerBootstrap.bootstrap()
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
                        logger.info("In withPath consumer");
//                        return CompletableFuture.completedFuture("That looks good");
                        return null;
                    }

                    @Override
                    public void handle(String data, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
                        logger.info("In withPath handler");
                    }
                })
                .registerHandler("/with/nested/path", new HttpAsyncRequestHandler<Object>() {
                    @Override
                    public HttpAsyncRequestConsumer<Object> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {

                        logger.info("In /with/nested/path handler");
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
                        logger.info("In /with/nested handler");
                        responder[0] = "/with/nested";
                        return null;
                    }

                    @Override
                    public void handle(Object data, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {

                    }
                })
                .create();



        //int port = httpServerIntegerPair.getSecond();
        //server = httpServerIntegerPair.getFirst();
        //System.out.println("port:" + port);

        localServer.start();
        localServer.getEndpoint().waitFor();
        InetSocketAddress address = (InetSocketAddress) localServer.getEndpoint().getAddress();
        Thread mainThread = new Thread(() -> {
            logger.info("starting server");
            try{
                localServer.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            }catch (InterruptedException e){
                logger.info("Interrupted");
            }
            logger.info("Server started");

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





