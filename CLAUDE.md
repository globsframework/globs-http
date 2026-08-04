# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The workspace-level `../CLAUDE.md` describes the globsframework ecosystem and conventions shared by all the
sibling repos (annotation pairs, no reflection on the hot path, per-repo release cycles). Read it too; what
follows is specific to `globs-http`.

## What this module is

`org.globsframework:globs-http` — expose an HTTP API whose routes, query parameters, headers, request bodies
and responses are all described by `GlobType`s instead of by POJOs/annotations, on top of **Apache HttpCore 5
async (NIO)**. The same declaration also generates the OpenAPI 3.0.1 document, itself a `Glob` serialized by
`globs-gson`.

Server-side only for routing; the client-side helpers in `GlobHttpUtils` build httpclient5 requests from a
Glob of parameters and are what the tests (and callers) use to talk to such a server.

## Build & test

Java 21 (`maven-compiler-plugin` source/target 21 — the `.github/workflows` still pin JDK 17, which cannot
compile this; the code uses switch pattern matching and sealed interfaces). Offline resolution works once
`~/.m2` is warm:

```bash
mvn -o test                                            # full suite, ~21 tests, 2 @Ignore'd
mvn -o test -Dtest=GlobHttpRequestHandlerTest#testThrowable
mvn -o test-compile
mvn -s settings.xml -B package                         # what CI runs (needs GH_MAVEN_REGISTRY_* env vars)
```

Tests are **JUnit 4** (`org.junit.Assert`, `@Before`/`@After`) — the newer sibling repos are JUnit 5, don't
copy their style here. `GlobHttpRequestHandlerTest` starts a real server on port 0 and drives it with a real
`CloseableHttpClient`, so failures usually show up as protocol-level assertions, not unit assertions. Test
`GlobType`s are static nested classes at the bottom of the test file, built with `DefaultGlobTypeBuilder`.

Dependencies are pinned: `globs` 5.3.0, `globs-gson` 5.1.0. A core change must be `mvn install`ed in
`../globsframework` and the version bumped here before it is visible.

## Architecture

### Declaration → dispatch, in two phases

Everything is walked once at startup; per-request work is table lookups and pre-built closures.

1. **Declaration.** `HttpServerRegister.register(url, pathParametersType)` returns a `Verb` (one per URL —
   calling `register` twice with the same URL returns the *same* `Verb`, which is how several HTTP methods
   share a path; a different path-parameter type for the same URL is an error). On it, `get`/`post`/`put`/
   `patch`/`delete` take the body/query/header `GlobType`s and a lambda, and return an `OperationInfo` for
   the fluent extras (`declareReturnType`, `withHeaderType`, `declareTags`, `comment`, `withExecutor`,
   `withSensitiveData`, `addHeader`). Each call builds a `DefaultHttpOperation` (Glob in / Glob out) or a
   `DefaultHttpDataOperation` (`getBin`/`postBin`, stream in / stream out), both implementing
   `MutableHttpDataOperation`.
2. **Wiring.** `GlobHttpApacheBuilder` walks `verbMap`, calls `Verb.complete()` → `DefaultHttpReceiver`,
   wraps each in a `GlobHttpRequestHandlerBuilder`, and registers it in the `RequestDispatcher`. It also
   logs one `HttpAPIDesc` JSON line per operation at INFO — that log is the de-facto API dump.
   `startAndWaitForStartup(bootstrap, 0)` starts the reactor and returns a `Server` carrying the bound port.

### Routing

`RequestDispatcher` holds `StrNode[]` **indexed by path-segment count**, so segment count narrows the
candidates before any string compare. Each `StrNode` keeps two lists of `SubStrNode`: exact routes and
wildcard routes. `SubStrNode.path` has `null` at every `{param}` position; `match` compares only the
non-null (literal) positions, and `matchExact` additionally requires equal length — without that a longer
request path would prefix-match a shorter route. Lookup tries the exact bucket for the request's length
first, then walks *shorter* buckets for wildcard routes.

A trailing `{param}` bound to a `StringArrayField` makes the route a wildcard that swallows the remaining
segments (`DefaultUrlMatcher.UrlWithWildard`); a `StringArrayField` anywhere else in the path is rejected.

`DefaultUrlMatcher` and `GlobHttpRequestHandlerBuilder.DefaultParamProcessor` both precompute a
`GlobHttpUtils.FromStringConverter` per field via a `FieldVisitor` — path params use no array separator,
query params split on `,`. `GlobField`/`GlobArrayField` parameters travel as URL-safe **Base64 of their JSON**
(symmetric in `GlobHttpUtils.glob2ValuePairList`).

### Request/response lifecycle (async, two threads)

`HttpRequestHttpAsyncServerExchangeTree` is the per-exchange `AsyncServerExchangeHandler`. It resolves the
route in `handleRequest`, creates a `DefaultGlobHttpRequestHandler`, and calls it immediately when there is
no entity; otherwise the body arrives through `consumeRequest`/`streamEnd`. A body that fits in a single
`ByteBuffer` is decoded straight from it; a fragmented one accumulates in `MultiByteArrayInputStream`.

The handler lambda runs on `operation.getExecutor()` (default `Runnable::run`, i.e. **the I/O reactor
thread** — use `withExecutor` to move blocking work off it). Its `CompletableFuture` completion writes the
response, while the reactor thread pulls bytes back out via `produceResponse`/`availableInResponse`. That
split is why `stream` is `volatile` and those two methods are `synchronized`; `availableInResponse` must
stay non-destructive (it is only a hint) or it races with `produceResponse` over the shared buffer.
Partially-written buffers are parked in `currentResponseBuffer` until the next output event.

### What a handler may return

`HttpOutputData` is a sealed interface: `asGlob`, `asGlobArray`, `asStream(stream, size)`. On top of that,
the returned `Glob`'s *type* selects the encoding in `DefaultGlobHttpRequestHandler`:

- `null` future / `null` Glob / zero-size stream → **204**.
- `GlobHttpContent.TYPE` → raw bytes with `mimeType`/`charset`/`statusCode`/`headers` taken from the Glob.
  `headers` (a `GlobArrayField` of `model/HttpHeader`) is how a handler sets a header whose value it only
  knows per request — a session id, a `Location`, an ETag. Declared headers are fixed at declaration time
  and cannot do that. Left unset, nothing is added.
- a type annotated `@HttpGlobResponse_` → the field annotated `@StatusCode_` (IntegerField) is the status and
  the field annotated `@HttpBodyData_` (GlobField or GlobArrayField) is the JSON body.
- anything else → JSON via `GSonUtils`, `application/json`, 200.

Responses are serialized into a `MultiBufferOutputStream` (growing direct `ByteBuffer` chain) so the
content-length is known before the response head is sent.

Errors: throwing (or failing the future with) `HttpException` sends its code + reason,
`HttpExceptionWithContent` sends its code + JSON content, anything else logs and sends 500.

`GlobFile.TYPE` is declared but **not handled** by `DefaultGlobHttpRequestHandler` — the corresponding test
is `@Ignore`d, and the only implementation is in the fully commented-out `OldGlobHttpRequestHandler` (the
pre-httpcore5 handler, kept as a 640-line comment; it also holds the unfinished multipart support).

### OpenAPI

`GlobOpenApi` builds the doc from the same `HttpServerRegister` — `initOpenApiDoc(port)` must be called
*after* the server is bound if the `servers[].url` should carry the real port. `registerOpenApi(globOpenApi)`
exposes it at `GET /api`, with `?scope=<tag>` filtering by the tags set through `declareTags`.

The doc is a Glob tree (`OpenApiType`, `OpenApiPath`, `OpenApiSchemaProperty`, …) whose JSON shape comes from
globs-gson annotations rather than from code: `@JsonValueAsField_` on `name` turns an array element into an
object key (that is how `paths` becomes `{"/test/{id}": …}`), `@JsonAsObject_` on the array does the same for
its container, and `@FieldName_("$ref")` handles names that are not valid Java identifiers. Adding a field to
the OpenAPI model means picking the right annotation, not writing a serializer. Round-tripping is one-way in
practice: encoding is correct, decoding `OpenApiType` back is a known gap (see `openApiScope`).

`buildSchema` memoizes per `GlobType` in the `schemas` map and emits `#/components/schemas/<name>` refs;
union fields get a synthetic `<Name>_union` wrapper type per branch.

### Headers

Request headers are parsed into a Glob of the operation's header type by
`DefaultGlobHttpRequestHandler.parseHeader`, matching **on the lower-cased name** — header names are
case-insensitive per RFC 9110 and clients do not agree on a casing, so a field declared `X-Trace-Id`
also matches `x-trace-id`. The per-`GlobType` lowercase map is built once and cached in `HEADER_FIELDS`.

Response headers declared through `Verb.addHeader` / `OperationInfo.addHeader` are written in
`sendHttpResponse`, the single choke point every response goes through — including error responses.
`ResponseHeaderTest` covers both directions plus the dynamic `GlobHttpContent.headers`.

## Gotchas

- `HttpTreatment` (3 args) vs `HttpTreatmentWithHeader` (4 args, adds the header Glob) vs
  `HttpDataTreatmentWithHeader` (stream in/out) — the `Verb` overloads pick between them. `.withHeaderType()`
  on a 3-arg `HttpTreatment` (the pattern the README shows) does cause the headers to be parsed into a Glob,
  but the default adapter drops that argument: only an interceptor sees it, never the lambda. To read headers
  in the handler, use the 4-arg overload. Header types are not reflected in the OpenAPI doc at all.
- Verb-level `addHeader` (on the `Verb`, not the `OperationInfo`) still goes to `DefaultHttpReceiver` and
  is **not** written: the handler only sees the `HttpOperation`. Use `OperationInfo.addHeader`.
- `GlobOpenApi` now declares `BigDecimal` as `{"type":"number","format":"big-decimal"}`, matching what
  globs-gson actually writes (`JsonFieldValueWithWriterVisitor.visitBigDecimal` → `jsonWriter.value(BigDecimal)`).
  It used to say `string`. **This changes the published document**, so clients generated from an older
  `/api` will disagree until regenerated. The mapping lives in two places that must stay in step — `subType`
  for bodies and responses, `OpenApiFieldVisitor` for query and path parameters.
- Null query/header Globs are replaced by a pre-instantiated empty Glob of the declared type before the
  lambda is called, but a *missing* query string yields `null` from `DefaultParamProcessor`, so
  `getQueryParamType()` returning `EMPTY` (not null) matters.
- `addRequestDecorator(InterceptBuilder)` wraps handlers, but only those registered **after** the call, and
  only the Glob-based ones — `getBin`/`postBin` bypass the interceptor entirely.
- The pom description still mentions etcd; there is no etcd code in this repo (see `globs-etcd`).
