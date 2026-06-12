# RuoYi Token Integration and Short-Term Memory Isolation

## Goal

Connect this standalone AgentScope service to the login sessions created by
RuoYi-Cloud-Plus. Requests use the RuoYi Sa-Token stored in a local Redis
instance. The service authenticates identity only: it does not evaluate roles,
menus, or API permissions.

Short-term chat memory must be isolated by both authenticated user ID and
conversation ID.

## Dependencies

Add the Sa-Token Spring Boot integration and the Redisson-backed Sa-Token DAO.
Use the versions available in the current development environment as the
implementation baseline:

- Sa-Token `1.44.0`
- Redisson `3.51.0`

Before deployment, verify these versions against the exact RuoYi-Cloud-Plus
release that creates the tokens. If that deployment uses different versions,
align this service to those versions before the end-to-end check.

Do not import the complete RuoYi common modules. Keeping the integration at the
Sa-Token and Redisson boundary avoids pulling RuoYi application internals into
this service.

## Redis Configuration

The default development Redis connection is:

- Host: `localhost`
- Port: `6379`
- Database: `0`
- Username: empty
- Password: empty

All values remain configurable through environment variables. The AgentScope
service and RuoYi must use compatible Sa-Token names, login types, Redis
database, and key prefixes so they address the same session records.

## Authentication Flow

1. The client sends the RuoYi token using the configured token header. The
   default header is `Authorization`; an optional `Bearer ` prefix is accepted.
2. `BearerTokenExtractor` extracts the raw token value.
3. `RuoyiSaTokenUserContext` asks Sa-Token for the login ID associated with the
   token.
4. A non-empty login ID is converted to a string and becomes the request
   `userId`.
5. Missing, invalid, expired, or logged-out tokens produce HTTP 401.
6. No role or permission checks are performed.

The `agentscope.auth.ruoyi.enabled` switch is removed or no longer exposed as a
runtime bypass. Chat requests always require a valid RuoYi identity.

## Short-Term Memory Isolation

Change `ShortTermMemoryStore` so both operations receive `userId` and
`conversationId`:

```java
void append(String userId, String conversationId, MemoryTurn turn);

List<MemoryTurn> recent(String userId, String conversationId);
```

`InMemoryShortTermMemoryStore` uses an immutable composite key containing both
values. Two users may use the same conversation ID without reading or
overwriting each other's turns.

`MemoryOrchestrator.prepare` and `recordTurn` pass the authenticated `userId`
through to short-term memory. Long-term memory already uses `userId` and keeps
its existing behavior.

## Error Handling

- Missing token: HTTP 401.
- Empty token: HTTP 401.
- Token not found in shared Redis: HTTP 401.
- Expired or logged-out token: HTTP 401.
- Redis or Sa-Token lookup failure: HTTP 401 at the current controller
  boundary, preserving the existing public API contract.

The response does not expose Redis connection details or internal Sa-Token
exceptions.

## Tests

Add or update tests for:

- Sa-Token dependency-backed token lookup without reflection.
- Missing and invalid tokens mapping to HTTP 401.
- The parsed login ID reaching `AgentChatService`.
- Two users using the same conversation ID receiving isolated short-term
  histories.
- Per-user and per-conversation turn limits.
- `MemoryOrchestrator` passing both identifiers to short-term memory.
- Application context startup with Redis configured for localhost and no
  password, while avoiding a required Redis connection during unrelated unit
  tests.

An end-to-end integration check should use a RuoYi-created token in the same
local Redis instance and verify that `/api/chat` accepts it.

## Out of Scope

- RuoYi login, logout, token refresh, user management, roles, and permissions.
- Persisting short-term memory in Redis.
- Changing long-term memory or knowledge indexing behavior.
- Importing the complete RuoYi framework into this repository.
