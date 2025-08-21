
Given an URLParameter, QueryParameter and HeaderType GlobType

```


bootstrap = AsyncServerBootstrap.bootstrap().setIOReactorConfig(config);

final HttpServerRegister httpServerRegister = new HttpServerRegister("EstablishmentServer/0.1");

httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
        .get(QueryParameter.TYPE, (body, url, queryParameters, header) -> {
            int id = url.getNotNull(URLParameter.id);
            // ...
            return CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                        .set(Response1.value, "some important information."));
        })
        .withHeaderType(HeaderType.TYPE)
        ;

GlobHttpApacheBuilder globHttpApacheBuilder = new GlobHttpApacheBuilder(httpServerRegister);
Server serverInstance = globHttpApacheBuilder.startAndWaitForStartup(bootstrap, 0);
server = serverInstance.getServer();
