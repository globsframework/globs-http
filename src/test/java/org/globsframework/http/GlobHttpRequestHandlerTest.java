package org.globsframework.http;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.FieldNameAnnotation;
import org.globsframework.metamodel.fields.LongField;
import org.globsframework.metamodel.fields.StringArrayField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.model.Glob;
import org.globsframework.utils.collections.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
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
                .setServerInfo("PriceServer/1.1")
                .setListenerPort(0)
                .setIOReactorConfig(config);

        BlockingQueue<Pair<Glob, Glob>> pairs = new LinkedBlockingDeque<>();
        HttpServerRegister httpServerRegister = new HttpServerRegister(bootstrap);
        httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
                .get(QueryParameter.TYPE, (body, url, queryParameters) -> {
                    pairs.add(Pair.makePair(url, queryParameters));
                    return null;
                });

        httpServerRegister.init();
        server = bootstrap.create();
        server.start();
        server.getEndpoint().waitFor();
        InetSocketAddress address = (InetSocketAddress) server.getEndpoint().getAddress();
        int port = address.getPort();
        System.out.println("port:" + port);

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
        server.shutdown(0, TimeUnit.MINUTES);
    }

    static public class URLParameter {
        public static GlobType TYPE;

        @FieldNameAnnotation("id")
        public static LongField ID;

        @FieldNameAnnotation("subId")
        public static LongField SUBID;

        static {
            GlobTypeLoaderFactory.create(URLParameter.class).load();
        }
    }

    static public class QueryParameter {
        public static GlobType TYPE;

        // add mendatory
        public static StringField NAME;

        public static StringArrayField INFO;

        static {
            GlobTypeLoaderFactory.create(QueryParameter.class).load();
        }
    }
}