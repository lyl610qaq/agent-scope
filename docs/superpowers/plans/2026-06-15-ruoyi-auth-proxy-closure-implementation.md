# RuoYi Authentication Proxy Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add API-only RuoYi login, current-session, and logout endpoints so a token returned by `/api/auth/login` can authenticate `/api/auth/me` and `/api/chat`, then be invalidated through `/api/auth/logout`.

**Architecture:** Add a validated immutable settings object, a narrowly scoped Spring `RestClient` client, and a controller that owns only `/api/auth/login`, `/api/auth/me`, and `/api/auth/logout`. Login and logout preserve upstream status/body/content type; `/me` and logout reuse the existing `AuthenticatedUserContext`, so this service does not implement passwords, token issuance, roles, or a second session model.

**Tech Stack:** Java 17, Spring Boot 4.0.6, Spring MVC, Spring `RestClient`, JDK `HttpClient`, Sa-Token, Redisson, JUnit 5, Mockito, MockMvc, JDK `HttpServer`

---

## Working-Tree Constraint

The repository already contains uncommitted layered-retrieval work. Do not
reset, revert, reformat, or stage unrelated files. Every commit command in this
plan stages only the paths listed for that task.

## File Structure

Create:

- `src/main/java/com/example/demoscope/RuoyiAuthProxySettings.java` - validated upstream URL, paths, token header, timeouts, and login-body limit.
- `src/main/java/com/example/demoscope/RuoyiAuthProxyResponse.java` - immutable upstream status/body/content-type value.
- `src/main/java/com/example/demoscope/RuoyiAuthProxyException.java` - transport failure classification without exposing secrets.
- `src/main/java/com/example/demoscope/RuoyiAuthProxyClient.java` - narrow login/logout `RestClient` calls.
- `src/main/java/com/example/demoscope/RuoyiAuthProxyConfig.java` - settings and redirect-disabled HTTP client beans.
- `src/main/java/com/example/demoscope/RuoyiAuthProxyController.java` - `/api/auth/login`, `/api/auth/me`, and `/api/auth/logout`.
- `src/test/java/com/example/demoscope/RuoyiAuthProxySettingsTest.java`
- `src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java`
- `src/test/java/com/example/demoscope/RuoyiAuthProxyControllerTest.java`
- `src/test/java/com/example/demoscope/RuoyiAuthFlowTest.java`

Modify:

- `src/main/java/com/example/demoscope/AgentRuntimeConfigController.java` - publish local auth endpoint metadata and token-header name.
- `src/main/resources/application.properties` - add upstream proxy settings.
- `src/main/resources/static/index.html` - stop reading removed `/api/config` fields; do not add login UI.
- `src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java`
- `src/test/java/com/example/demoscope/AgentChatControllerTest.java`
- `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`
- `src/test/java/com/example/demoscope/OpenAiModelConfigTest.java`

## Task 1: Validate RuoYi Proxy Settings

**Files:**
- Create: `src/main/java/com/example/demoscope/RuoyiAuthProxySettings.java`
- Create: `src/test/java/com/example/demoscope/RuoyiAuthProxySettingsTest.java`

- [ ] **Step 1: Write the failing settings tests**

Create `RuoyiAuthProxySettingsTest.java`:

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class RuoyiAuthProxySettingsTest {

    @Test
    void buildsValidatedLoginAndLogoutUris() {
        RuoyiAuthProxySettings settings = new RuoyiAuthProxySettings(
                URI.create("http://localhost:8081"),
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                16_384);

        assertEquals(URI.create("http://localhost:8081/auth/login"), settings.loginUri());
        assertEquals(URI.create("http://localhost:8081/auth/logout"), settings.logoutUri());
    }

    @Test
    void rejectsBlankOrNonHttpUpstreamUrls() {
        assertThrows(IllegalArgumentException.class, () -> settings(null, "/auth/login"));
        assertThrows(
                IllegalArgumentException.class,
                () -> settings(URI.create("file:///tmp/ruoyi"), "/auth/login"));
    }

    @Test
    void rejectsUnsafePathsHeadersTimeoutsAndBodyLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> settings(URI.create("http://localhost:8081"), "auth/login"));
        assertThrows(
                IllegalArgumentException.class,
                () -> settings(URI.create("http://localhost:8081"), "//evil.example/auth/login"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuoyiAuthProxySettings(
                        URI.create("http://localhost:8081"),
                        "/auth/login",
                        "/auth/logout",
                        "Bad Header",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        16_384));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuoyiAuthProxySettings(
                        URI.create("http://localhost:8081"),
                        "/auth/login",
                        "/auth/logout",
                        "Authorization",
                        Duration.ZERO,
                        Duration.ofSeconds(10),
                        16_384));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuoyiAuthProxySettings(
                        URI.create("http://localhost:8081"),
                        "/auth/login",
                        "/auth/logout",
                        "Authorization",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        0));
    }

    private RuoyiAuthProxySettings settings(URI baseUrl, String loginPath) {
        return new RuoyiAuthProxySettings(
                baseUrl,
                loginPath,
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                16_384);
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthProxySettingsTest' test
```

Expected: test compilation fails because `RuoyiAuthProxySettings` does not
exist.

- [ ] **Step 3: Implement the immutable settings object**

Create `RuoyiAuthProxySettings.java`:

```java
package com.example.demoscope;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RuoyiAuthProxySettings(
        URI baseUrl,
        String loginPath,
        String logoutPath,
        String tokenHeaderName,
        Duration connectTimeout,
        Duration readTimeout,
        int maxLoginBodyBytes) {

    private static final Pattern HEADER_NAME =
            Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    public RuoyiAuthProxySettings {
        baseUrl = validateBaseUrl(baseUrl);
        loginPath = validatePath(loginPath, "loginPath");
        logoutPath = validatePath(logoutPath, "logoutPath");
        tokenHeaderName = Objects.requireNonNull(tokenHeaderName, "tokenHeaderName").trim();
        if (!HEADER_NAME.matcher(tokenHeaderName).matches()) {
            throw new IllegalArgumentException("tokenHeaderName must be a valid HTTP header name");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (maxLoginBodyBytes <= 0) {
            throw new IllegalArgumentException("maxLoginBodyBytes must be positive");
        }
    }

    public URI loginUri() {
        return baseUrl.resolve(loginPath);
    }

    public URI logoutUri() {
        return baseUrl.resolve(logoutPath);
    }

    private static URI validateBaseUrl(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null) {
            throw new IllegalArgumentException("RuoYi base URL must be an absolute HTTP URL");
        }
        String scheme = value.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("RuoYi base URL must use HTTP or HTTPS");
        }
        if (value.getRawQuery() != null || value.getRawFragment() != null) {
            throw new IllegalArgumentException("RuoYi base URL must not contain a query or fragment");
        }
        return value;
    }

    private static String validatePath(String value, String name) {
        String path = Objects.requireNonNull(value, name).trim();
        URI uri = URI.create(path);
        if (!path.startsWith("/")
                || path.startsWith("//")
                || uri.isAbsolute()
                || uri.getRawAuthority() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !uri.normalize().toString().equals(path)) {
            throw new IllegalArgumentException(name + " must be a normalized absolute path");
        }
        return path;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
```

- [ ] **Step 4: Run the settings tests and verify GREEN**

Run the command from Step 2.

Expected: `RuoyiAuthProxySettingsTest` passes.

- [ ] **Step 5: Commit the settings unit**

```powershell
git add -- src/main/java/com/example/demoscope/RuoyiAuthProxySettings.java src/test/java/com/example/demoscope/RuoyiAuthProxySettingsTest.java
git commit -m "feat: validate ruoyi auth proxy settings"
```

## Task 2: Preserve Upstream Login and Logout Responses

**Files:**
- Create: `src/main/java/com/example/demoscope/RuoyiAuthProxyResponse.java`
- Create: `src/main/java/com/example/demoscope/RuoyiAuthProxyException.java`
- Create: `src/main/java/com/example/demoscope/RuoyiAuthProxyClient.java`
- Create: `src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java`

- [ ] **Step 1: Write failing HTTP client contract tests**

Create `RuoyiAuthProxyClientTest.java` with a local JDK HTTP server:

```java
package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class RuoyiAuthProxyClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsExactLoginJsonAndPreservesUpstreamResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        startServer("/auth/login", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] response = "{\"code\":401,\"msg\":\"captcha required\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        RuoyiAuthProxyClient client = client(settings(baseUrl(), Duration.ofSeconds(2)));
        byte[] login = """
                {"tenantId":"000000","username":"demo","password":"secret","code":"1234","uuid":"u-1"}
                """.strip().getBytes(StandardCharsets.UTF_8);

        RuoyiAuthProxyResponse response = client.login(login);

        assertEquals(new String(login, StandardCharsets.UTF_8), requestBody.get());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, contentType.get());
        assertEquals(401, response.statusCode());
        assertEquals(MediaType.parseMediaType("application/problem+json"), response.contentType());
        assertArrayEquals(
                "{\"code\":401,\"msg\":\"captcha required\"}".getBytes(StandardCharsets.UTF_8),
                response.body());
    }

    @Test
    void forwardsOnlyTheConfiguredLogoutTokenValue() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer("/auth/logout", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("X-Token"));
            byte[] response = "{\"code\":200}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        RuoyiAuthProxySettings settings = new RuoyiAuthProxySettings(
                baseUrl(),
                "/auth/login",
                "/auth/logout",
                "X-Token",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                16_384);

        RuoyiAuthProxyResponse response =
                client(settings).logout("Bearer token-123");

        assertEquals("Bearer token-123", authorization.get());
        assertEquals(200, response.statusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.contentType());
        assertArrayEquals(
                "{\"code\":200}".getBytes(StandardCharsets.UTF_8),
                response.body());
    }

    @Test
    void preservesUpstreamServerErrorsWithoutThrowing() throws Exception {
        startServer("/auth/login", exchange -> {
            byte[] response = "{\"code\":500,\"msg\":\"upstream failure\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        RuoyiAuthProxyResponse response =
                client(settings(baseUrl(), Duration.ofSeconds(2)))
                        .login("{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(500, response.statusCode());
        assertArrayEquals(
                "{\"code\":500,\"msg\":\"upstream failure\"}"
                        .getBytes(StandardCharsets.UTF_8),
                response.body());
    }

    @Test
    void classifiesUnavailableAndTimeoutTransportFailures() {
        RuoyiAuthProxyClient unavailable = client(
                settings(URI.create("http://localhost:8081"), Duration.ofSeconds(1)),
                (uri, method) -> {
                    throw new ConnectException("connection refused");
                });
        RuoyiAuthProxyException unavailableError = assertThrows(
                RuoyiAuthProxyException.class,
                () -> unavailable.login("{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(RuoyiAuthProxyException.Kind.UNAVAILABLE, unavailableError.kind());

        RuoyiAuthProxyClient timeout = client(
                settings(URI.create("http://localhost:8081"), Duration.ofSeconds(1)),
                (uri, method) -> {
                    throw new java.net.SocketTimeoutException("timed out");
                });
        RuoyiAuthProxyException timeoutError = assertThrows(
                RuoyiAuthProxyException.class,
                () -> timeout.login("{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(RuoyiAuthProxyException.Kind.TIMEOUT, timeoutError.kind());
    }

    private void startServer(String path, com.sun.net.httpserver.HttpHandler handler)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        server.start();
    }

    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private RuoyiAuthProxySettings settings(URI baseUrl, Duration readTimeout) {
        return new RuoyiAuthProxySettings(
                baseUrl,
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(1),
                readTimeout,
                16_384);
    }

    private RuoyiAuthProxyClient client(RuoyiAuthProxySettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        return client(settings, requestFactory);
    }

    private RuoyiAuthProxyClient client(
            RuoyiAuthProxySettings settings,
            ClientHttpRequestFactory requestFactory) {
        return new RuoyiAuthProxyClient(
                RestClient.builder().requestFactory(requestFactory).build(),
                settings);
    }
}
```

- [ ] **Step 2: Run the client test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthProxyClientTest' test
```

Expected: test compilation fails because the proxy client, response, and
exception types do not exist.

- [ ] **Step 3: Implement the response and transport exception values**

Create `RuoyiAuthProxyResponse.java`:

```java
package com.example.demoscope;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public record RuoyiAuthProxyResponse(
        int statusCode,
        MediaType contentType,
        byte[] body) {

    public RuoyiAuthProxyResponse {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public ResponseEntity<byte[]> toResponseEntity() {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(statusCode);
        if (contentType != null) {
            builder.contentType(contentType);
        }
        return builder.body(body());
    }
}
```

Create `RuoyiAuthProxyException.java`:

```java
package com.example.demoscope;

public final class RuoyiAuthProxyException extends RuntimeException {

    public enum Kind {
        UNAVAILABLE,
        TIMEOUT
    }

    private final Kind kind;

    public RuoyiAuthProxyException(Kind kind, Throwable cause) {
        super(kind == Kind.TIMEOUT
                ? "RuoYi authentication request timed out"
                : "RuoYi authentication service is unavailable", cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
```

- [ ] **Step 4: Implement the narrow RestClient client**

Create `RuoyiAuthProxyClient.java`:

```java
package com.example.demoscope;

import java.net.HttpTimeoutException;
import java.net.SocketTimeoutException;

import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

public class RuoyiAuthProxyClient {

    private final RestClient restClient;
    private final RuoyiAuthProxySettings settings;

    public RuoyiAuthProxyClient(
            RestClient restClient,
            RuoyiAuthProxySettings settings) {
        this.restClient = restClient;
        this.settings = settings;
    }

    public RuoyiAuthProxyResponse login(byte[] jsonBody) {
        return exchange(restClient.post()
                .uri(settings.loginUri())
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(jsonBody.length)
                .body(jsonBody));
    }

    public RuoyiAuthProxyResponse logout(String tokenHeaderValue) {
        return exchange(restClient.post()
                .uri(settings.logoutUri())
                .header(settings.tokenHeaderName(), tokenHeaderValue));
    }

    private RuoyiAuthProxyResponse exchange(
            RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange((httpRequest, response) ->
                    new RuoyiAuthProxyResponse(
                            response.getStatusCode().value(),
                            response.getHeaders().getContentType(),
                            response.getBody().readAllBytes()));
        } catch (ResourceAccessException exception) {
            RuoyiAuthProxyException.Kind kind = isTimeout(exception)
                    ? RuoyiAuthProxyException.Kind.TIMEOUT
                    : RuoyiAuthProxyException.Kind.UNAVAILABLE;
            throw new RuoyiAuthProxyException(kind, exception);
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
```

- [ ] **Step 5: Run the client tests and verify GREEN**

Run the command from Step 2.

Expected: all `RuoyiAuthProxyClientTest` tests pass.

- [ ] **Step 6: Commit the proxy client unit**

```powershell
git add -- src/main/java/com/example/demoscope/RuoyiAuthProxyResponse.java src/main/java/com/example/demoscope/RuoyiAuthProxyException.java src/main/java/com/example/demoscope/RuoyiAuthProxyClient.java src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java
git commit -m "feat: proxy ruoyi authentication requests"
```

## Task 3: Configure the Redirect-Disabled Auth Client

**Files:**
- Create: `src/main/java/com/example/demoscope/RuoyiAuthProxyConfig.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java`

- [ ] **Step 1: Add a failing redirect and settings-bean test**

Extend `RuoyiAuthProxyClientTest`:

```java
@Test
void doesNotFollowUpstreamRedirects() throws Exception {
    startServer("/auth/login", exchange -> {
        exchange.getResponseHeaders().set("Location", "/unexpected-target");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    });
    RuoyiAuthProxySettings settings = settings(baseUrl(), Duration.ofSeconds(2));
    RestClient restClient = RuoyiAuthProxyConfig.restClient(settings);

    RuoyiAuthProxyResponse response =
            new RuoyiAuthProxyClient(restClient, settings)
                    .login("{}".getBytes(StandardCharsets.UTF_8));

    assertEquals(302, response.statusCode());
}

@Test
void configurationRejectsBlankBaseUrl() {
    assertThrows(
            IllegalArgumentException.class,
            () -> RuoyiAuthProxyConfig.settings(
                    "",
                    "/auth/login",
                    "/auth/logout",
                    "Authorization",
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(10),
                    16_384));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthProxyClientTest' test
```

Expected: compilation fails because `RuoyiAuthProxyConfig` does not exist.

- [ ] **Step 3: Implement configuration beans**

Create `RuoyiAuthProxyConfig.java`:

```java
package com.example.demoscope;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RuoyiAuthProxyConfig {

    @Bean
    RuoyiAuthProxySettings ruoyiAuthProxySettings(
            @Value("${agentscope.auth.ruoyi.base-url:}") String baseUrl,
            @Value("${agentscope.auth.ruoyi.login-path:/auth/login}") String loginPath,
            @Value("${agentscope.auth.ruoyi.logout-path:/auth/logout}") String logoutPath,
            @Value("${agentscope.auth.ruoyi.token-name:Authorization}") String tokenHeaderName,
            @Value("${agentscope.auth.ruoyi.connect-timeout:3s}") Duration connectTimeout,
            @Value("${agentscope.auth.ruoyi.read-timeout:10s}") Duration readTimeout,
            @Value("${agentscope.auth.ruoyi.max-login-body-bytes:16384}") int maxLoginBodyBytes) {
        return settings(
                baseUrl,
                loginPath,
                logoutPath,
                tokenHeaderName,
                connectTimeout,
                readTimeout,
                maxLoginBodyBytes);
    }

    @Bean
    RestClient ruoyiAuthRestClient(RuoyiAuthProxySettings settings) {
        return restClient(settings);
    }

    @Bean
    RuoyiAuthProxyClient ruoyiAuthProxyClient(
            RestClient ruoyiAuthRestClient,
            RuoyiAuthProxySettings settings) {
        return new RuoyiAuthProxyClient(ruoyiAuthRestClient, settings);
    }

    static RuoyiAuthProxySettings settings(
            String baseUrl,
            String loginPath,
            String logoutPath,
            String tokenHeaderName,
            Duration connectTimeout,
            Duration readTimeout,
            int maxLoginBodyBytes) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AGENTSCOPE_RUOYI_BASE_URL must be configured");
        }
        return new RuoyiAuthProxySettings(
                URI.create(baseUrl),
                loginPath,
                logoutPath,
                tokenHeaderName,
                connectTimeout,
                readTimeout,
                maxLoginBodyBytes);
    }

    static RestClient restClient(RuoyiAuthProxySettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
```

- [ ] **Step 4: Add the production properties**

Append to `application.properties`:

```properties
agentscope.auth.ruoyi.base-url=${AGENTSCOPE_RUOYI_BASE_URL:}
agentscope.auth.ruoyi.login-path=${AGENTSCOPE_RUOYI_LOGIN_PATH:/auth/login}
agentscope.auth.ruoyi.logout-path=${AGENTSCOPE_RUOYI_LOGOUT_PATH:/auth/logout}
agentscope.auth.ruoyi.connect-timeout=${AGENTSCOPE_RUOYI_CONNECT_TIMEOUT:3s}
agentscope.auth.ruoyi.read-timeout=${AGENTSCOPE_RUOYI_READ_TIMEOUT:10s}
agentscope.auth.ruoyi.max-login-body-bytes=${AGENTSCOPE_RUOYI_MAX_LOGIN_BODY_BYTES:16384}
```

Keep the existing
`agentscope.auth.ruoyi.token-name=${AGENTSCOPE_RUOYI_TOKEN_NAME:Authorization}`
line as the single token-header property.

- [ ] **Step 5: Run the client and settings tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthProxySettingsTest,RuoyiAuthProxyClientTest' test
```

Expected: all tests pass.

- [ ] **Step 6: Commit the HTTP configuration**

```powershell
git add -- src/main/java/com/example/demoscope/RuoyiAuthProxyConfig.java src/main/resources/application.properties src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java
git commit -m "feat: configure ruoyi auth proxy client"
```

## Task 4: Expose Login, Session, and Logout Endpoints

**Files:**
- Create: `src/main/java/com/example/demoscope/RuoyiAuthProxyController.java`
- Create: `src/test/java/com/example/demoscope/RuoyiAuthProxyControllerTest.java`

- [ ] **Step 1: Write failing controller contract tests**

Create `RuoyiAuthProxyControllerTest.java`:

```java
package com.example.demoscope;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuoyiAuthProxyControllerTest {

    private RuoyiAuthProxyClient client;
    private MutableUserContext userContext;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        client = mock(RuoyiAuthProxyClient.class);
        userContext = new MutableUserContext();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RuoyiAuthProxyController(client, userContext, settings()))
                .build();
    }

    @Test
    void loginForwardsExactJsonAndPreservesResponse() throws Exception {
        when(client.login(any())).thenReturn(new RuoyiAuthProxyResponse(
                201,
                MediaType.APPLICATION_JSON,
                "{\"data\":{\"access_token\":\"token-123\"}}"
                        .getBytes(StandardCharsets.UTF_8)));
        String body = """
                {"tenantId":"000000","username":"demo","password":"secret","extra":{"code":"42"}}
                """.strip();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"data\":{\"access_token\":\"token-123\"}}"));

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(client).login(bodyCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                body,
                new String(bodyCaptor.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    void loginRejectsMissingWrongAndOversizedBodies() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("123456789"))
                .andExpect(status().isPayloadTooLarge());
        verify(client, never()).login(any());
    }

    @Test
    void meReturnsAuthenticatedUserIdAndRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("user-42"));

        userContext.fail = true;
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutValidatesLocallyThenForwardsExactHeader() throws Exception {
        when(client.logout("Bearer token-123")).thenReturn(new RuoyiAuthProxyResponse(
                200,
                MediaType.APPLICATION_JSON,
                "{\"code\":200}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":200}"));

        verify(client).logout("Bearer token-123");
    }

    @Test
    void invalidLogoutNeverContactsUpstream() throws Exception {
        userContext.fail = true;

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verify(client, never()).logout(any());
    }

    @Test
    void mapsTransportFailuresWithoutExposingInternalDetails() throws Exception {
        when(client.login(any())).thenThrow(new RuoyiAuthProxyException(
                RuoyiAuthProxyException.Kind.UNAVAILABLE,
                new IllegalStateException("http://internal-ruoyi:8080 refused")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("internal-ruoyi"))));
    }

    private static RuoyiAuthProxySettings settings() {
        return new RuoyiAuthProxySettings(
                URI.create("http://localhost:8081"),
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                8);
    }

    private static final class MutableUserContext implements AuthenticatedUserContext {
        private boolean fail;

        @Override
        public String requireUserId(HttpServletRequest request) {
            if (fail) {
                throw new UnauthenticatedUserException("invalid authentication token");
            }
            return "user-42";
        }
    }
}
```

- [ ] **Step 2: Run the controller test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthProxyControllerTest' test
```

Expected: test compilation fails because `RuoyiAuthProxyController` does not
exist.

- [ ] **Step 3: Implement the controller**

Create `RuoyiAuthProxyController.java`:

```java
package com.example.demoscope;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class RuoyiAuthProxyController {

    private final RuoyiAuthProxyClient proxyClient;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final RuoyiAuthProxySettings settings;

    public RuoyiAuthProxyController(
            RuoyiAuthProxyClient proxyClient,
            AuthenticatedUserContext authenticatedUserContext,
            RuoyiAuthProxySettings settings) {
        this.proxyClient = proxyClient;
        this.authenticatedUserContext = authenticatedUserContext;
        this.settings = settings;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> login(HttpServletRequest request) {
        return proxy(() -> proxyClient.login(readLoginBody(request)));
    }

    @GetMapping("/me")
    public CurrentSessionResponse me(HttpServletRequest request) {
        return new CurrentSessionResponse(true, requireUserId(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<byte[]> logout(HttpServletRequest request) {
        requireUserId(request);
        String tokenHeaderValue = request.getHeader(settings.tokenHeaderName());
        return proxy(() -> proxyClient.logout(tokenHeaderValue));
    }

    private byte[] readLoginBody(HttpServletRequest request) {
        int limit = settings.maxLoginBodyBytes();
        if (request.getContentLengthLong() > limit) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "login request body is too large");
        }
        try {
            byte[] body = request.getInputStream().readNBytes(limit + 1);
            if (body.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "login request body must not be empty");
            }
            if (body.length > limit) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "login request body is too large");
            }
            return body;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "login request body could not be read",
                    exception);
        }
    }

    private String requireUserId(HttpServletRequest request) {
        try {
            return authenticatedUserContext.requireUserId(request);
        } catch (UnauthenticatedUserException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    exception.getMessage(),
                    exception);
        }
    }

    private ResponseEntity<byte[]> proxy(
            Supplier<RuoyiAuthProxyResponse> operation) {
        try {
            return operation.get().toResponseEntity();
        } catch (RuoyiAuthProxyException exception) {
            HttpStatus status = exception.kind() == RuoyiAuthProxyException.Kind.TIMEOUT
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, exception.getMessage());
        }
    }

    public record CurrentSessionResponse(boolean authenticated, String userId) {
    }
}
```

- [ ] **Step 4: Add the timeout controller assertion**

Add to `RuoyiAuthProxyControllerTest`:

```java
@Test
void mapsUpstreamTimeoutToGatewayTimeout() throws Exception {
    when(client.login(any())).thenThrow(new RuoyiAuthProxyException(
            RuoyiAuthProxyException.Kind.TIMEOUT,
            new java.net.SocketTimeoutException("timed out")));

    mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isGatewayTimeout());
}
```

- [ ] **Step 5: Run controller and client tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthProxySettingsTest,RuoyiAuthProxyClientTest,RuoyiAuthProxyControllerTest' test
```

Expected: all proxy tests pass.

- [ ] **Step 6: Commit the API endpoints**

```powershell
git add -- src/main/java/com/example/demoscope/RuoyiAuthProxyController.java src/test/java/com/example/demoscope/RuoyiAuthProxyControllerTest.java
git commit -m "feat: expose ruoyi login session and logout APIs"
```

## Task 5: Publish the Local Auth Contract

**Files:**
- Modify: `src/main/java/com/example/demoscope/AgentRuntimeConfigController.java`
- Modify: `src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/test/java/com/example/demoscope/AgentChatControllerTest.java`

- [ ] **Step 1: Write the failing runtime-contract assertions**

In `AgentRuntimeConfigControllerTest`, add:

```java
.andExpect(jsonPath("$.ruoyiAuth.loginPath").value("/api/auth/login"))
.andExpect(jsonPath("$.ruoyiAuth.logoutPath").value("/api/auth/logout"))
.andExpect(jsonPath("$.ruoyiAuth.mePath").value("/api/auth/me"))
.andExpect(jsonPath("$.ruoyiAuth.tokenHeaderName").value("X-RuoYi-Token"))
.andExpect(jsonPath("$.ruoyiAuth.baseUrl").doesNotExist())
```

Add this property to the existing `@SpringBootTest(properties = { ... })`:

```java
"agentscope.auth.ruoyi.token-name=X-RuoYi-Token",
"agentscope.auth.ruoyi.base-url=http://127.0.0.1:18081",
```

In `AgentChatControllerTest.previewHtmlContainsDemoTitleAndRuoyiTokenInput`,
add:

```java
.andExpect(content().string(
        org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("config.embeddingModel"))))
.andExpect(content().string(
        org.hamcrest.Matchers.containsString("config.ruoyiAuth")))
```

Also set the required test property:

```java
@SpringBootTest(properties = {
        "agentscope.openai.api-key=",
        "agentscope.auth.ruoyi.base-url=http://127.0.0.1:18081"
})
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=AgentRuntimeConfigControllerTest,AgentChatControllerTest' test
```

Expected: runtime config assertions fail because `ruoyiAuth` is absent, and
the static page still references removed legacy fields.

- [ ] **Step 3: Extend the runtime config response**

Add a `tokenHeaderName` constructor value to
`AgentRuntimeConfigController`, pass it into the response, and add:

```java
public record RuoyiAuthConfigResponse(
        String loginPath,
        String logoutPath,
        String mePath,
        String tokenHeaderName) {

    static RuoyiAuthConfigResponse local(String tokenHeaderName) {
        return new RuoyiAuthConfigResponse(
                "/api/auth/login",
                "/api/auth/logout",
                "/api/auth/me",
                tokenHeaderName);
    }
}
```

The top-level response becomes:

```java
public record AgentRuntimeConfigResponse(
        String modelName,
        String baseUrl,
        boolean apiKeyConfigured,
        RetrievalConfigResponse knowledge,
        RetrievalConfigResponse longTermMemory,
        RuoyiAuthConfigResponse ruoyiAuth) {
}
```

Construct it with:

```java
RuoyiAuthConfigResponse.local(tokenHeaderName)
```

Do not add the upstream base URL, Redis settings, credentials, or timeouts.

- [ ] **Step 4: Remove stale static-page config reads**

In `index.html`, replace `loadRuntimeConfig()` with:

```javascript
async function loadRuntimeConfig() {
    try {
        const response = await fetch("/api/config");
        const config = await response.json();
        const auth = config.ruoyiAuth || {};
        const knowledge = config.knowledge || {};
        const longTermMemory = config.longTermMemory || {};
        tokenHeaderName = auth.tokenHeaderName || "Authorization";
        runtimeBox.textContent = [
            "聊天模型：" + (config.modelName || "未配置") +
                (config.apiKeyConfigured ? " / API Key 已配置" : " / 缺少 API Key"),
            "RuoYi 登录接口：" + (auth.loginPath || "/api/auth/login"),
            "RuoYi 鉴权 Header：" + tokenHeaderName,
            "知识召回：" + (knowledge.vectorTopK || 0) +
                " → " + (knowledge.finalTopN || 0),
            "长期记忆召回：" + (longTermMemory.vectorTopK || 0) +
                " → " + (longTermMemory.finalTopN || 0),
            "当前会话：" + conversationId
        ].join("\n");
    } catch (error) {
        runtimeBox.textContent = "运行状态加载失败：" + error.message;
    }
}
```

Do not add login controls or call the login endpoint from the page.

- [ ] **Step 5: Run runtime config and static-page tests**

Run the command from Step 2.

Expected: both test classes pass.

- [ ] **Step 6: Commit the public API metadata**

```powershell
git add -- src/main/java/com/example/demoscope/AgentRuntimeConfigController.java src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java src/main/resources/static/index.html src/test/java/com/example/demoscope/AgentChatControllerTest.java
git commit -m "feat: publish ruoyi auth API contract"
```

## Task 6: Prove Login-to-Chat-to-Logout HTTP Flow

**Files:**
- Create: `src/test/java/com/example/demoscope/RuoyiAuthFlowTest.java`
- Modify: `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`
- Modify: `src/test/java/com/example/demoscope/OpenAiModelConfigTest.java`

- [ ] **Step 1: Write the failing HTTP flow test**

Create `RuoyiAuthFlowTest.java`:

```java
package com.example.demoscope;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuoyiAuthFlowTest {

    @Test
    void loginTokenAuthenticatesMeAndChatThenLogoutInvalidatesIt() throws Exception {
        MutableSession session = new MutableSession();
        RuoyiAuthProxyController authController = new RuoyiAuthProxyController(
                new FakeProxyClient(session),
                session,
                settings());
        AgentChatController chatController = new AgentChatController(
                (userId, conversationId, message) -> "hello " + userId,
                session);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(authController, chatController)
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("token-123"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-42"));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"interview-a","message":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("hello user-42"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer token-123"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/chat")
                        .header("Authorization", "Bearer token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"interview-a","message":"hello"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private RuoyiAuthProxySettings settings() {
        return new RuoyiAuthProxySettings(
                URI.create("http://localhost:8081"),
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                16_384);
    }

    private static final class MutableSession implements AuthenticatedUserContext {
        private boolean active;

        @Override
        public String requireUserId(HttpServletRequest request) {
            String value = request.getHeader("Authorization");
            if (!active || !"Bearer token-123".equals(value)) {
                throw new UnauthenticatedUserException("invalid authentication token");
            }
            return "user-42";
        }
    }

    private static final class FakeProxyClient extends RuoyiAuthProxyClient {
        private final MutableSession session;

        FakeProxyClient(MutableSession session) {
            super(RestClient.create(), settings());
            this.session = session;
        }

        @Override
        public RuoyiAuthProxyResponse login(byte[] jsonBody) {
            session.active = true;
            return new RuoyiAuthProxyResponse(
                    200,
                    MediaType.APPLICATION_JSON,
                    "{\"data\":{\"access_token\":\"token-123\"}}"
                            .getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public RuoyiAuthProxyResponse logout(String tokenHeaderValue) {
            session.active = false;
            return new RuoyiAuthProxyResponse(
                    200,
                    MediaType.APPLICATION_JSON,
                    "{\"code\":200}".getBytes(StandardCharsets.UTF_8));
        }
    }
}
```

Add this missing import:

```java
import org.springframework.web.client.RestClient;
```

- [ ] **Step 2: Run the flow test and verify its initial result**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthFlowTest' test
```

Expected before Tasks 1-4 are complete: compilation fails on the missing proxy
types. Expected when executing tasks sequentially: the new flow test passes and
proves the three controllers compose correctly.

- [ ] **Step 3: Supply the mandatory upstream URL to full-context tests**

Add:

```java
"agentscope.auth.ruoyi.base-url=http://127.0.0.1:18081"
```

to the `@SpringBootTest(properties = { ... })` arrays in:

- `DemoScopeApplicationTests`
- `OpenAiModelConfigTest` (convert its single property to an array)

`AgentChatControllerTest` and `AgentRuntimeConfigControllerTest` were updated
in Task 5.

- [ ] **Step 4: Run all authentication and chat tests**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B '-Dtest=RuoyiAuthProxySettingsTest,RuoyiAuthProxyClientTest,RuoyiAuthProxyControllerTest,RuoyiAuthFlowTest,RuoyiSaTokenUserContextTest,DefaultSaTokenFacadeTest,AgentChatRuoyiAuthTest,AgentChatControllerTest,AgentRuntimeConfigControllerTest,DemoScopeApplicationTests' test
```

Expected: all focused tests pass without contacting the configured dummy
upstream URL.

- [ ] **Step 5: Run the full suite and package**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B test
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B -DskipTests package
git diff --check
```

Expected:

- Maven test build succeeds with zero failures.
- Package build succeeds.
- `git diff --check` exits 0.

- [ ] **Step 6: Commit the flow verification**

```powershell
git add -- src/test/java/com/example/demoscope/RuoyiAuthFlowTest.java src/test/java/com/example/demoscope/DemoScopeApplicationTests.java src/test/java/com/example/demoscope/OpenAiModelConfigTest.java
git commit -m "test: verify ruoyi login chat and logout flow"
```

## Task 7: Verify Against the Real RuoYi Deployment

**Files:**
- No source changes required.

- [ ] **Step 1: Start the required services**

Ensure:

- RuoYi is reachable at the configured `AGENTSCOPE_RUOYI_BASE_URL`.
- RuoYi and AgentScope use the same Redis host, port, database, Sa-Token token
  name, login type, and key namespace.
- AgentScope has `OPENAI_API_KEY` configured when a successful model response
  is required.

- [ ] **Step 2: Execute the real login call**

Set environment variables:

```powershell
$agentScopeBaseUrl = $env:AGENTSCOPE_SMOKE_BASE_URL
$loginBody = Get-Content -Raw -Encoding UTF8 $env:RUOYI_LOGIN_BODY_FILE
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "$agentScopeBaseUrl/api/auth/login" `
  -ContentType 'application/json' `
  -Body $loginBody
$token = $login.data.access_token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw 'RuoYi login response did not contain data.access_token'
}
```

Expected: login succeeds and `$token` is non-empty.

- [ ] **Step 3: Verify current session and chat authentication**

```powershell
$headers = @{ Authorization = "Bearer $token" }
$me = Invoke-RestMethod `
  -Method Get `
  -Uri "$agentScopeBaseUrl/api/auth/me" `
  -Headers $headers
if ($me.userId -ne $env:EXPECTED_RUOYI_USER_ID) {
    throw "Expected user $env:EXPECTED_RUOYI_USER_ID but received $($me.userId)"
}

$chat = Invoke-WebRequest `
  -Method Post `
  -Uri "$agentScopeBaseUrl/api/chat" `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body '{"conversationId":"ruoyi-auth-smoke","message":"hello"}' `
  -SkipHttpErrorCheck
if ($chat.StatusCode -eq 401) {
    throw 'The RuoYi token was not accepted by /api/chat'
}
```

Expected: `/api/auth/me` returns the configured expected user and `/api/chat`
does not return 401. A separate 500 caused by a missing model key is an
environment configuration failure, not an authentication failure.

- [ ] **Step 4: Logout and prove invalidation**

```powershell
Invoke-WebRequest `
  -Method Post `
  -Uri "$agentScopeBaseUrl/api/auth/logout" `
  -Headers $headers `
  -SkipHttpErrorCheck

$afterLogout = Invoke-WebRequest `
  -Method Get `
  -Uri "$agentScopeBaseUrl/api/auth/me" `
  -Headers $headers `
  -SkipHttpErrorCheck
if ($afterLogout.StatusCode -ne 401) {
    throw "Expected 401 after logout but received $($afterLogout.StatusCode)"
}
```

Expected: logout returns the RuoYi response and the same token receives 401
from `/api/auth/me`.

- [ ] **Step 5: Record verification status**

In the implementation completion message, report:

- The RuoYi URL host used, without credentials or query parameters.
- Whether login, `/me`, `/api/chat` authentication, logout, and post-logout 401
  each passed.
- If the real environment was unavailable, state explicitly that automated
  contract tests passed but production login closure remains unverified.
