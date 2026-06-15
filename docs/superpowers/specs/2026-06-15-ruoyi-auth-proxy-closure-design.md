# RuoYi Authentication Proxy Closure

Date: 2026-06-15

## Goal

Complete the API-only flow from RuoYi login to authenticated AgentScope
operations without adding a login UI or importing the complete RuoYi
application.

The AgentScope service exposes three narrow authentication endpoints:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

Login and logout are proxied to the configured RuoYi service. Session identity
continues to be resolved locally through the existing Sa-Token and shared Redis
integration. A token returned by login can therefore be used with
`/api/auth/me` and `/api/chat`, and logout invalidates the same upstream RuoYi
session.

## Scope

This design includes:

- A narrow proxy for RuoYi `/auth/login` and `/auth/logout`.
- A local authenticated-session endpoint that returns the current user ID.
- Configurable upstream URL, paths, token header, and HTTP timeouts.
- Status, body, and content-type passthrough for upstream login and logout
  responses.
- Automated contract tests and an environment-backed end-to-end verification
  procedure.

This design does not include:

- A login page or other UI work.
- Local password validation or token issuance.
- Registration, captcha generation, token refresh, user management, roles,
  menus, or permissions.
- A general `/auth/**` reverse proxy.
- Interview-agent orchestration.

## Chosen Approach

Use a narrow Spring `RestClient` proxy.

The service owns explicit login and logout routes rather than forwarding an
arbitrary path. This keeps the exposed surface small, allows precise header
filtering, and preserves RuoYi as the sole owner of credentials, captcha,
tenant, account-state, and token lifecycle rules.

The alternatives were rejected:

- A general authentication reverse proxy exposes more upstream capabilities
  than this service needs.
- A local login implementation would duplicate RuoYi security rules and could
  create incompatible Sa-Token sessions.

## API Contracts

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

The request body is the original RuoYi login JSON. The proxy does not bind it
to a local DTO, remove fields, or add credentials. This preserves support for
the deployed RuoYi contract, including fields such as `tenantId`, `username`,
`password`, `code`, and `uuid`.

The proxy sends the body to:

```text
{agentscope.auth.ruoyi.base-url}{agentscope.auth.ruoyi.login-path}
```

The upstream HTTP status, body, and `Content-Type` are returned to the caller.
The default login path is `/auth/login`.

Login is the only authentication endpoint that does not require an existing
token.

### Current Session

```http
GET /api/auth/me
Authorization: Bearer <RuoYi token>
```

The configured token header is used instead of hard-coding
`Authorization`. `Bearer ` remains optional because the existing
`BearerTokenExtractor` accepts both forms.

On success:

```json
{
  "authenticated": true,
  "userId": "42"
}
```

`/api/auth/me` does not call an upstream user-profile endpoint and does not
return roles, menus, permissions, tenant metadata, or private account data. It
uses `AuthenticatedUserContext` to resolve the login ID from the shared
Sa-Token Redis session.

Missing, blank, expired, logged-out, or invalid tokens return HTTP 401.

### Logout

```http
POST /api/auth/logout
Authorization: Bearer <RuoYi token>
```

Before proxying, the service validates the token through
`AuthenticatedUserContext`. A missing or invalid token returns HTTP 401 and is
not sent upstream.

For a valid session, the proxy sends a POST request to:

```text
{agentscope.auth.ruoyi.base-url}{agentscope.auth.ruoyi.logout-path}
```

Only the configured token header is copied from the incoming request. The
upstream HTTP status, body, and `Content-Type` are returned unchanged. The
default logout path is `/auth/logout`.

After a successful RuoYi logout, the shared Redis session is expected to be
invalid. Subsequent calls to `/api/auth/me` and `/api/chat` with that token
must return HTTP 401.

## Components

### RuoyiAuthProxyController

Responsibilities:

- Expose the three local endpoints.
- Enforce JSON for login.
- Validate authentication for `/me` and logout.
- Convert local authentication failures to HTTP 401.
- Delegate upstream calls without interpreting RuoYi business response JSON.

It does not build upstream URLs from caller input and does not log request or
response bodies.

### RuoyiAuthProxyClient

Responsibilities:

- Use Spring `RestClient` for login and logout calls.
- Forward only the approved body and token header.
- Preserve upstream status, response bytes, and response content type.
- Translate connectivity failures to HTTP 502.
- Translate request or response timeouts to HTTP 504.

The client does not follow arbitrary caller-provided URLs and is not reusable
as a general proxy.

### RuoyiAuthProperties

Configuration:

```properties
agentscope.auth.ruoyi.base-url=${AGENTSCOPE_RUOYI_BASE_URL:}
agentscope.auth.ruoyi.login-path=${AGENTSCOPE_RUOYI_LOGIN_PATH:/auth/login}
agentscope.auth.ruoyi.logout-path=${AGENTSCOPE_RUOYI_LOGOUT_PATH:/auth/logout}
agentscope.auth.ruoyi.token-name=${AGENTSCOPE_RUOYI_TOKEN_NAME:Authorization}
agentscope.auth.ruoyi.connect-timeout=${AGENTSCOPE_RUOYI_CONNECT_TIMEOUT:3s}
agentscope.auth.ruoyi.read-timeout=${AGENTSCOPE_RUOYI_READ_TIMEOUT:10s}
```

Startup must reject a blank or malformed base URL when the proxy endpoints are
enabled. Paths must be absolute path components and must not contain a scheme,
host, query, or fragment.

The same token-header setting is shared by the proxy, token extractor, runtime
configuration response, and tests.

### Existing Authentication Components

The following components remain the identity authority inside this service:

- `BearerTokenExtractor`
- `RuoyiSaTokenUserContext`
- `SaTokenFacade`
- `RedissonSaTokenDao`

No second token parser or local session representation is introduced.

## Data Flow

### Login to Chat

```text
Client
  -> POST /api/auth/login
  -> RuoyiAuthProxyController
  -> RuoyiAuthProxyClient
  -> RuoYi POST /auth/login
  <- RuoYi status/body/content-type
  <- client extracts returned token
  -> POST /api/chat with configured token header
  -> BearerTokenExtractor
  -> RuoyiSaTokenUserContext
  -> shared Redis Sa-Token session
  -> AgentChatService
```

### Logout

```text
Client
  -> POST /api/auth/logout with token
  -> AuthenticatedUserContext validates token
  -> RuoyiAuthProxyClient forwards token
  -> RuoYi POST /auth/logout
  -> RuoYi invalidates shared Redis session
  <- upstream response passthrough
  -> later /api/auth/me or /api/chat returns 401
```

## Header and Body Policy

Login forwards:

- Request body bytes.
- `Content-Type: application/json`.

Logout forwards:

- The configured token header exactly as received.
- An empty request body unless the deployed RuoYi endpoint requires otherwise.

The proxy does not forward:

- `Cookie`
- `Host`
- `Forwarded` or `X-Forwarded-*`
- Caller-supplied destination information
- Unrelated authorization or custom headers

Response forwarding is limited to:

- HTTP status
- Response body bytes
- `Content-Type`

Hop-by-hop headers and upstream cookies are not exposed.

## Security and Operational Rules

- Passwords, captcha values, login bodies, login responses, and complete tokens
  must never be logged.
- The upstream base URL is service-side configuration only.
- Login accepts only `application/json`.
- The application applies a bounded login request size. Oversized requests
  return HTTP 413 before contacting RuoYi.
- Redirects from the upstream authentication endpoints are not followed.
- Connect and read timeouts are finite and configurable.
- Upstream error bodies are passed through, but transport exception messages
  and internal endpoint details are not exposed.
- The proxy does not add retry behavior because repeating login requests can
  trigger account lock, captcha, or rate-limit side effects.

## Error Handling

| Condition | Local result |
| --- | --- |
| Login body is missing, empty, oversized, or not JSON | 400, 413, or 415 |
| `/me` or logout token is missing or invalid | 401 |
| RuoYi returns 4xx or 5xx | Same status, body, and content type |
| RuoYi cannot be reached | 502 |
| RuoYi request times out | 504 |
| Proxy configuration is invalid | Application startup failure |

Error responses created locally must not include passwords, tokens, Redis
details, or the configured RuoYi URL.

## Runtime Configuration Contract

`GET /api/config` must expose enough non-secret information for API clients to
use the authentication endpoints consistently:

```json
{
  "ruoyiAuth": {
    "loginPath": "/api/auth/login",
    "logoutPath": "/api/auth/logout",
    "mePath": "/api/auth/me",
    "tokenHeaderName": "Authorization"
  }
}
```

It must not expose the upstream RuoYi base URL, credentials, tokens, Redis
connection details, or timeout values.

The existing retrieval configuration fields remain available. The static page
must not depend on removed legacy configuration fields, even though login UI
work is outside this feature.

## Testing

### Unit and HTTP Contract Tests

- Login forwards exact JSON bytes and JSON content type.
- Additional RuoYi login fields are preserved without DTO changes.
- Login preserves upstream success and failure status, body, and content type.
- Login rejects missing, non-JSON, and oversized bodies.
- `/me` returns the authenticated user ID.
- `/me` returns 401 for missing and invalid tokens.
- Logout rejects invalid tokens without calling upstream.
- Logout forwards only the configured token header.
- Logout preserves upstream status, body, and content type.
- Connection failures map to 502.
- Timeouts map to 504.
- `/api/config` returns the local auth paths and configured token header but no
  secrets or upstream URL.

Tests use a local HTTP test server or mock transport for RuoYi and a controlled
`AuthenticatedUserContext`. They must assert outgoing paths, headers, bodies,
and call counts rather than only mocking controller return values.

### Integration Verification

An environment-backed smoke test requires:

- A running RuoYi service.
- This service and RuoYi using compatible Sa-Token settings.
- Both services connected to the same Redis database and key namespace.
- Valid login credentials and any required tenant/captcha values.
- A configured model API key if the chat response itself must succeed.

The smoke sequence is:

1. Call `POST /api/auth/login`.
2. Extract the token from the RuoYi response.
3. Call `GET /api/auth/me` and verify the expected `userId`.
4. Call `POST /api/chat` with the same token and verify it passes
   authentication.
5. Call `POST /api/auth/logout`.
6. Verify `/api/auth/me` and `/api/chat` return 401 for the logged-out token.

The automated suite can prove the proxy and local session contracts, but the
feature is not considered production-verified until this smoke sequence passes
against the actual RuoYi deployment and shared Redis instance.

## Acceptance Criteria

- API clients can log in through `POST /api/auth/login` without knowing the
  upstream RuoYi URL.
- Login request and response contracts remain compatible with the deployed
  RuoYi `/auth/login`.
- A returned token resolves to the expected user through `/api/auth/me`.
- The same token authorizes `/api/chat`.
- Logout is proxied to RuoYi and the token subsequently fails local identity
  resolution.
- Upstream status and JSON bodies are preserved for login and logout.
- Transport failures produce stable 502 or 504 responses without leaking
  secrets.
- No general-purpose proxy, local credential validation, or login UI is added.
