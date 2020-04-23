package org.globsframework.http;

import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.globsframework.metamodel.GlobType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpServerRegister {
    final ServerBootstrap serverBootstrap;
    final Map<String, Verb> verbMap = new HashMap<>();

    public HttpServerRegister(ServerBootstrap serverBootstrap) {
        this.serverBootstrap = serverBootstrap;
    }

    public Verb register(String url, GlobType queryUrl) {
        Verb verb = new Verb(url, queryUrl);
        verbMap.put(url, verb);
        return verb;
    }

    public HttpServer init(){
        for (Map.Entry<String, Verb> stringVerbEntry : verbMap.entrySet()) {
            Verb verb = stringVerbEntry.getValue();
            GlobHttpRequestHandler globHttpRequestHandler = new GlobHttpRequestHandler(verb.complete());
            serverBootstrap.registerHandler(globHttpRequestHandler.createRegExp(), globHttpRequestHandler);
        }
        return serverBootstrap.create();
    }

    public class Verb {
        private final String url;
        private final GlobType queryUrl;
        private List<HttpOperation> operations = new ArrayList<>();

        public Verb(String url, GlobType queryUrl) {
            this.url = url;
            this.queryUrl = queryUrl;
        }

        public void get(GlobType paramType, HttpTreatment httpTreatment) {
            operations.add(new DefaultHttpOperation(HttpOp.get, null, paramType, httpTreatment));
        }

        public void post(GlobType bodyParam, GlobType paramType, HttpTreatment httpTreatment) {
            operations.add(new DefaultHttpOperation(HttpOp.post, bodyParam, paramType, httpTreatment));
        }

        public void delete(GlobType paramType, HttpTreatment httpTreatment) {
            operations.add(new DefaultHttpOperation(HttpOp.delete, null, paramType, httpTreatment));
        }

        HttpReceiver complete() {
            return new DefaultHttpReceiver(url, queryUrl, operations.toArray(new HttpOperation[0]));
        }
    }
}
