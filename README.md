# Globs HTTP

Expose an HTTP API whose routes, path and query parameters, headers, bodies and responses are all described
by [GlobType](https://globsframework.org)s instead of POJOs and annotations, on top of **Apache HttpCore 5
async (NIO)**. The same declaration also produces the **OpenAPI 3.0.1** document — itself a Glob, serialized
by `globs-gson` — so the documentation cannot drift from the server.

Everything is walked once at startup; per request the work is a table lookup and pre-built closures, no
reflection.

## Requirements

- Java 21
- `org.globsframework:globs` and `globs-gson`, plus `org.apache.httpcomponents.core5:httpcore5`

## Installation

```xml
<dependency>
    <groupId>org.globsframework</groupId>
    <artifactId>globs-http</artifactId>
    <version>5.2.0</version>
</dependency>
```

## Declaring an API

`register(url, pathParametersType)` returns a `Verb` for that URL; `get`/`post`/`put`/`patch`/`delete` on it
take the body, query and header `GlobType`s and the handler. Registering the same URL twice returns the
*same* `Verb`, which is how several methods share one path.

```java
HttpServerRegister httpServerRegister = new HttpServerRegister("EstablishmentServer/0.1");

httpServerRegister.register("/test/{id}/TOTO/{subId}", URLParameter.TYPE)
        .get(QueryParameter.TYPE, HeaderType.TYPE, (body, url, queryParameters, header) -> {
            int id = url.getNotNull(URLParameter.id);
            // ...
            return CompletableFuture.completedFuture(Response1.TYPE.instantiate()
                    .set(Response1.value, "some important information."));
        })
        .declareReturnType(Response1.TYPE)
        .declareTags(new String[]{"test"})
        .comment("what this route does")
        .withExecutor(executor);

AsyncServerBootstrap bootstrap = AsyncServerBootstrap.bootstrap().setIOReactorConfig(config);
GlobHttpApacheBuilder globHttpApacheBuilder = new GlobHttpApacheBuilder(httpServerRegister);
Server server = globHttpApacheBuilder.startAndWaitForStartup(bootstrap, 0);
int port = server.getPort();
```

The handler runs on `Runnable::run` by default — that is **the I/O reactor thread**. Use `withExecutor` (a
virtual-thread executor, say) as soon as it blocks.

`getBin` / `postBin` are the streaming variants: an `InputStream` in, an `HttpOutputData.asStream(stream,
size)` out, with no Glob encoding in between.

### Parameters

- **path** — a `{param}` segment is filled into the path-parameter Glob. A trailing `{param}` bound to a
  `StringArrayField` swallows the remaining segments; a `StringArrayField` anywhere else in the path is
  rejected.
- **query** — one field per parameter, arrays split on `,`. A `GlobField` / `GlobArrayField` parameter
  travels as URL-safe **Base64 of its JSON**.
- **headers** — parsed into a Glob of the declared header type, matched on the **lower-cased** name, so a
  field declared `X-Trace-Id` also matches `x-trace-id`.

> The overloads decide what the lambda gets: `get(queryType, treatment)` is an `HttpTreatment`
> (body, pathParameters, queryParameters), `get(queryType, headerType, treatment)` is an
> `HttpTreatmentWithHeader` and adds the header Glob. `.withHeaderType()` on a 3-argument `HttpTreatment`
> does cause the headers to be parsed, but the default adapter drops that argument — only an interceptor
> sees it, never the lambda. Header types do not appear in the OpenAPI document.

### What a handler returns

`HttpOutputData` is a sealed interface (`asGlob`, `asGlobArray`, `asStream`), and the returned Glob's *type*
selects the encoding:

| Returned | Response |
| --- | --- |
| `null` future, `null` Glob, or a zero-size stream | **204** |
| `GlobHttpContent.TYPE` | raw bytes, with `mimeType` / `charset` / `statusCode` / `headers` read from the Glob |
| a type carrying `HttpGlobResponse` | status from the `StatusCode` field, JSON body from the `HttpBodyData` field |
| anything else | JSON via `GSonUtils`, `application/json`, 200 |

`GlobHttpContent.headers` is how a handler sets a header it only knows per request — a session id, a
`Location`, an ETag. Headers known at declaration time go through `OperationInfo.addHeader` instead (the
`Verb`-level `addHeader` is not written).

Errors: throw (or fail the future with) `HttpException` for a code + reason, `HttpExceptionWithContent` for a
code + JSON content; anything else is logged and answered 500.

## OpenAPI

```java
GlobOpenApi globOpenApi = new GlobOpenApi(httpServerRegister);
httpServerRegister.registerOpenApi(globOpenApi);          // GET /api
// after the server is bound, so servers[].url carries the real port:
globOpenApi.initOpenApiDoc(server.getPort());
```

`GET /api?scope=<tag>` filters by the tags declared with `declareTags`. The document is built from the same
registrations — the return type declared with `declareReturnType`, the body and parameter types, the
comments — and each `GlobType` becomes a `#/components/schemas/<name>` entry, memoized so a type shared by
several routes is emitted once.

## Calling such an API

`GlobHttpUtils` builds httpclient5 requests from a Glob of parameters, using the same conversions as the
server side:

```java
HttpGet get = GlobHttpUtils.createGet("/test/" + id + "/TOTO/" + subId, queryParams);
HttpPost post = GlobHttpUtils.createPost("/student", queryParams, bodyGlob);
```

## Interceptors

`addRequestDecorator(InterceptBuilder)` wraps handlers — but only those registered **after** the call, and
only the Glob-based ones: `getBin` / `postBin` bypass interception.

## Building

```bash
mvn -o test                                              # JUnit 4
mvn -o test -Dtest=GlobHttpRequestHandlerTest#testThrowable
```

`GlobHttpRequestHandlerTest` starts a real server on port 0 and drives it with a real `CloseableHttpClient`.
`CLAUDE.md` documents the routing structure, the async lifecycle and the known gaps (`GlobFile.TYPE`,
one-way OpenAPI decoding).

## License

Apache License 2.0 — see <https://www.apache.org/licenses/LICENSE-2.0.txt>.

## Links

- [Globs Framework](https://globsframework.org)
- [GitHub repository](https://github.com/globsframework/globs-http)
- [globs-examples](https://github.com/globsframework/globs-examples) — a runnable server wiring http + sql + graphql
