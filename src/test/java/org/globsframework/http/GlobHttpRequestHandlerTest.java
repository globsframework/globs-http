package org.globsframework.http;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.globsframework.json.GSonUtils;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.metamodel.fields.LongField;
import org.globsframework.metamodel.fields.StringArrayField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.model.Glob;
import org.globsframework.utils.Files;
import org.globsframework.utils.collections.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class GlobHttpRequestHandlerTest {

    private HttpServer server;

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
        HttpServerRegister httpServerRegister = new HttpServerRegister("PriceServer/1.1");
        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, new HttpTreatment() {
                    public CompletableFuture<Glob> consume(Glob body, Glob url, Glob queryParameters) throws Exception {
                        pairs.add(Pair.makePair(url, queryParameters));
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
                .setGzipCompress()
                .post(BodyContent.TYPE, null, (body, url, queryParameters) -> {
                    return CompletableFuture.completedFuture(BodyContent.TYPE.instantiate()
                            .set(BodyContent.DATA, "some important information."));
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

        HttpGet httpGet = new HttpGet("/test/123/TOTO/4567?name=ZERZE&info=A,B,C,D");
        HttpResponse httpResponse = httpclient.execute(target, httpGet);
        Assert.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        Pair<Glob, Glob> poll = pairs.poll(2, TimeUnit.SECONDS);
        Assert.assertNotNull(poll);
        Assert.assertEquals(123, poll.getFirst().get(URLParameter.ID, 0));
        Assert.assertEquals(4567, poll.getFirst().get(URLParameter.SUBID, 0));
        Assert.assertEquals("ZERZE", poll.getSecond().get(QueryParameter.NAME));
        Assert.assertArrayEquals(new String[]{"A", "B", "C", "D"}, poll.getSecond().get(QueryParameter.INFO));


        HttpGet httpGetFile = new HttpGet("/query");
        HttpResponse httpFileResponse = httpclient.execute(target, httpGetFile);
        Assert.assertEquals(200, httpFileResponse.getStatusLine().getStatusCode());
        Assert.assertEquals("[]", Files.loadStreamToString(httpFileResponse.getEntity().getContent(), "UTF-8"));

        HttpPost httpPostFile = new HttpPost("/query");
        HttpResponse httpPostResponse = httpclient.execute(target, httpPostFile);
        Assert.assertEquals(200, httpPostResponse.getStatusLine().getStatusCode());
        Assert.assertEquals("{\"DATA\":\"some important information.\"}", Files.loadStreamToString(httpPostResponse.getEntity().getContent(), "UTF-8"));

        server.shutdown(0, TimeUnit.MINUTES);
        Assert.assertFalse(httpContent.exists());
        httpServerIntegerPair.getFirst().shutdown(0, TimeUnit.DAYS);
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

    static public class QueryParameter {
        public static GlobType TYPE;

        // add mendatory
        public static StringField NAME;

        public static StringArrayField INFO;

        static {
            GlobTypeLoaderFactory.create(QueryParameter.class, true).load();
        }
    }

    static public class BodyContent {
        public static GlobType TYPE;

        public static StringField DATA;

        static {
            GlobTypeLoaderFactory.create(BodyContent.class).load();
        }
    }
}