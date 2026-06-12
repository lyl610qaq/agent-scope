# RuoYi Token and Short-Term Memory Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve RuoYi Sa-Token identities from a local passwordless Redis instance, return HTTP 401 for invalid identities, and isolate short-term memory by authenticated user and conversation.

**Architecture:** Add Sa-Token Core and Redisson Core directly instead of the Spring Boot 3 starters, because this project uses Spring Boot 4. A small Redisson-backed `SaTokenDao` is registered with `SaManager`; the existing controller remains responsible for mapping identity failures to HTTP 401. No role or permission APIs are called. Short-term memory receives an explicit composite scope of `userId` and `conversationId`.

**Tech Stack:** Java 17, Spring Boot 4.0.6, Sa-Token 1.44.0, Redisson 3.51.0, JUnit 6, Mockito, Maven

---

## File Structure

- Modify `pom.xml`: declare the verified Sa-Token and Redisson versions and dependencies.
- Create `src/main/java/com/example/demoscope/RedissonSaTokenDao.java`: adapt Redisson buckets and TTLs to the Sa-Token DAO contract.
- Create `src/main/java/com/example/demoscope/RuoyiAuthConfig.java`: create the local Redis client and register the DAO with Sa-Token.
- Modify `src/main/java/com/example/demoscope/DefaultSaTokenFacade.java`: replace reflection with the typed Sa-Token API.
- Modify `src/main/java/com/example/demoscope/MemoryConfig.java`: stop constructing the Sa-Token facade and user context there.
- Modify `src/main/resources/application.properties`: define localhost, database 0, empty username, and empty password defaults.
- Modify `src/main/java/com/example/demoscope/ShortTermMemoryStore.java`: include `userId` in reads and writes.
- Modify `src/main/java/com/example/demoscope/InMemoryShortTermMemoryStore.java`: use an immutable `(userId, conversationId)` key.
- Modify `src/main/java/com/example/demoscope/MemoryOrchestrator.java`: pass the authenticated user through short-term memory operations.
- Modify related tests and create focused DAO/configuration tests.

### Task 1: Add Sa-Token and Redisson Build Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add dependency version properties**

Add:

```xml
<sa-token.version>1.44.0</sa-token.version>
<redisson.version>3.51.0</redisson.version>
```

- [ ] **Step 2: Add the direct dependencies**

Add:

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-core</artifactId>
    <version>${sa-token.version}</version>
</dependency>

<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>${redisson.version}</version>
</dependency>
```

Do not add `sa-token-spring-boot3-starter` or
`redisson-spring-boot-starter`; their current baselines target Spring Boot 3.

- [ ] **Step 3: Verify dependency resolution and compilation**

Run:

```powershell
$env:JAVA_HOME='C:\computerSoftware\jdk21'
& 'C:\computerSoftware\maven\apache-maven-3.9.12\bin\mvn.cmd' -B -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit dependency setup**

```powershell
git add -- pom.xml
git commit -m "build: add sa-token and redisson dependencies"
```

### Task 2: Implement the Redisson-Backed Sa-Token DAO

**Files:**
- Create: `src/test/java/com/example/demoscope/RedissonSaTokenDaoTest.java`
- Create: `src/main/java/com/example/demoscope/RedissonSaTokenDao.java`

- [ ] **Step 1: Write failing string and TTL tests**

Create tests that use mocked `RedissonClient` and `RBucket<Object>` instances:

```java
@Test
void readsRuoYiLoginIdAsString() {
    RedissonClient redisson = mock(RedissonClient.class);
    RBucket<Object> bucket = mock(RBucket.class);
    when(redisson.<Object>getBucket("satoken:login:token:token-123")).thenReturn(bucket);
    when(bucket.get()).thenReturn(42L);

    RedissonSaTokenDao dao = new RedissonSaTokenDao(redisson);

    assertEquals("42", dao.get("satoken:login:token:token-123"));
}

@Test
void convertsRedisMillisecondsToSaTokenSeconds() {
    RedissonClient redisson = mock(RedissonClient.class);
    RBucket<Object> bucket = mock(RBucket.class);
    when(redisson.<Object>getBucket("key")).thenReturn(bucket);
    when(bucket.remainTimeToLive()).thenReturn(5_500L);

    assertEquals(5L, new RedissonSaTokenDao(redisson).getTimeout("key"));
}

@Test
void preservesSaTokenMissingAndPermanentTimeoutValues() {
    RedissonClient redisson = mock(RedissonClient.class);
    RBucket<Object> bucket = mock(RBucket.class);
    when(redisson.<Object>getBucket("key")).thenReturn(bucket);
    when(bucket.remainTimeToLive()).thenReturn(-2L, -1L);
    RedissonSaTokenDao dao = new RedissonSaTokenDao(redisson);

    assertEquals(SaTokenDao.NOT_VALUE_EXPIRE, dao.getTimeout("key"));
    assertEquals(SaTokenDao.NEVER_EXPIRE, dao.getTimeout("key"));
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=RedissonSaTokenDaoTest test
```

Expected: test compilation fails because `RedissonSaTokenDao` does not exist.

- [ ] **Step 3: Implement minimal Redisson DAO**

Implement `SaTokenDaoByObjectFollowString` with:

```java
public final class RedissonSaTokenDao implements SaTokenDaoByObjectFollowString {

    private final RedissonClient redisson;

    public RedissonSaTokenDao(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public String get(String key) {
        Object value = bucket(key).get();
        return value == null ? null : value.toString();
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            bucket(key).set(value);
        } else if (timeout > 0) {
            bucket(key).set(value, timeout, TimeUnit.SECONDS);
        }
    }

    @Override
    public void update(String key, String value) {
        bucket(key).setAndKeepTTL(value);
    }

    @Override
    public void delete(String key) {
        bucket(key).delete();
    }

    @Override
    public long getTimeout(String key) {
        long milliseconds = bucket(key).remainTimeToLive();
        if (milliseconds == NOT_VALUE_EXPIRE || milliseconds == NEVER_EXPIRE) {
            return milliseconds;
        }
        return TimeUnit.MILLISECONDS.toSeconds(milliseconds);
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            bucket(key).clearExpire();
        } else if (timeout > 0) {
            bucket(key).expire(timeout, TimeUnit.SECONDS);
        }
    }

    @Override
    public List<String> searchData(
            String prefix, String keyword, int start, int size, boolean sortType) {
        List<String> keys = StreamSupport.stream(
                        redisson.getKeys().getKeysByPattern(prefix + "*" + keyword + "*").spliterator(),
                        false)
                .sorted(sortType ? Comparator.naturalOrder() : Comparator.reverseOrder())
                .toList();
        int from = Math.min(Math.max(0, start), keys.size());
        int to = size < 0 ? keys.size() : Math.min(keys.size(), from + size);
        return keys.subList(from, to);
    }

    private RBucket<Object> bucket(String key) {
        return redisson.getBucket(key);
    }
}
```

Add tests for `set`, `update`, `delete`, `updateTimeout`, and pagination before
considering the DAO complete.

- [ ] **Step 4: Run DAO tests to verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=RedissonSaTokenDaoTest test
```

Expected: all `RedissonSaTokenDaoTest` tests pass.

- [ ] **Step 5: Commit the Redis DAO**

```powershell
git add -- src/main/java/com/example/demoscope/RedissonSaTokenDao.java src/test/java/com/example/demoscope/RedissonSaTokenDaoTest.java
git commit -m "feat: add redisson sa-token dao"
```

### Task 3: Configure Local Redis and Typed Sa-Token Lookup

**Files:**
- Create: `src/test/java/com/example/demoscope/RuoyiAuthConfigTest.java`
- Create: `src/test/java/com/example/demoscope/DefaultSaTokenFacadeTest.java`
- Create: `src/main/java/com/example/demoscope/RuoyiAuthConfig.java`
- Modify: `src/main/java/com/example/demoscope/DefaultSaTokenFacade.java`
- Modify: `src/main/java/com/example/demoscope/MemoryConfig.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Write the failing passwordless Redis configuration test**

Expose a package-private pure configuration method and test:

```java
@Test
void createsLocalPasswordlessRedisConfiguration() {
    Config config = RuoyiAuthConfig.redissonConfig("localhost", 6379, 0, "", "");
    SingleServerConfig server = config.useSingleServer();

    assertEquals("redis://localhost:6379", server.getAddress());
    assertEquals(0, server.getDatabase());
    assertNull(server.getUsername());
    assertNull(server.getPassword());
}
```

- [ ] **Step 2: Write the failing typed facade test**

Use an in-memory Sa-Token DAO and restore the global DAO after the test:

```java
@Test
void resolvesLoginIdThroughSaTokenApi() {
    SaTokenDao previous = SaManager.getSaTokenDao();
    SaTokenDaoDefaultImpl dao = new SaTokenDaoDefaultImpl();
    String key = StpUtil.getStpLogic().splicingKeyTokenValue("token-123");
    try {
        SaManager.setSaTokenDao(dao);
        dao.set(key, "42", 60);

        assertEquals("42", new DefaultSaTokenFacade().getLoginIdByToken("token-123"));
    } finally {
        SaManager.setSaTokenDao(previous);
    }
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=RuoyiAuthConfigTest,DefaultSaTokenFacadeTest test
```

Expected: `RuoyiAuthConfig` is missing and the facade test exposes the current
configuration gap. The facade behavior test may already pass after Task 1;
replacing reflection in Step 5 is a type-safety refactor protected by that test.

- [ ] **Step 4: Implement Redis and Sa-Token configuration**

Create `RuoyiAuthConfig` with:

```java
@Configuration(proxyBeanMethods = false)
public class RuoyiAuthConfig {

    @Bean(destroyMethod = "shutdown")
    RedissonClient ruoyiRedissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password) {
        return Redisson.create(redissonConfig(host, port, database, username, password));
    }

    @Bean
    SaTokenDao ruoyiSaTokenDao(RedissonClient redissonClient) {
        SaTokenDao dao = new RedissonSaTokenDao(redissonClient);
        SaManager.setSaTokenDao(dao);
        return dao;
    }

    @Bean
    SaTokenFacade saTokenFacade() {
        return new DefaultSaTokenFacade();
    }

    @Bean
    AuthenticatedUserContext authenticatedUserContext(
            BearerTokenExtractor tokenExtractor,
            SaTokenFacade saTokenFacade) {
        return new RuoyiSaTokenUserContext(tokenExtractor, saTokenFacade);
    }

    static Config redissonConfig(
            String host, int port, int database, String username, String password) {
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec());
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        if (StringUtils.hasText(username)) {
            server.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        return config;
    }
}
```

Remove the `SaTokenFacade` and `AuthenticatedUserContext` bean methods from
`MemoryConfig`.

- [ ] **Step 5: Replace reflection with the typed API**

Change `DefaultSaTokenFacade.getLoginIdByToken` to:

```java
@Override
public Object getLoginIdByToken(String token) {
    return StpUtil.getLoginIdByToken(token);
}
```

- [ ] **Step 6: Complete local Redis properties**

Set:

```properties
spring.data.redis.host=${AGENTSCOPE_RUOYI_REDIS_HOST:localhost}
spring.data.redis.port=${AGENTSCOPE_RUOYI_REDIS_PORT:6379}
spring.data.redis.database=${AGENTSCOPE_RUOYI_REDIS_DATABASE:0}
spring.data.redis.username=${AGENTSCOPE_RUOYI_REDIS_USERNAME:}
spring.data.redis.password=${AGENTSCOPE_RUOYI_REDIS_PASSWORD:}
```

Keep the configured RuoYi token header. Remove the environment-controlled
authentication bypass; the runtime config may continue to report
`ruoyiAuthEnabled=true` for API compatibility.

- [ ] **Step 7: Run authentication tests to verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=RuoyiAuthConfigTest,DefaultSaTokenFacadeTest,RuoyiSaTokenUserContextTest test
```

Expected: all selected tests pass.

- [ ] **Step 8: Commit RuoYi authentication configuration**

```powershell
git add -- pom.xml src/main/java/com/example/demoscope/RuoyiAuthConfig.java src/main/java/com/example/demoscope/DefaultSaTokenFacade.java src/main/java/com/example/demoscope/MemoryConfig.java src/main/resources/application.properties src/test/java/com/example/demoscope/RuoyiAuthConfigTest.java src/test/java/com/example/demoscope/DefaultSaTokenFacadeTest.java
git commit -m "feat: resolve ruoyi identities from local redis"
```

### Task 4: Isolate Short-Term Memory by User and Conversation

**Files:**
- Modify: `src/test/java/com/example/demoscope/InMemoryShortTermMemoryStoreTest.java`
- Modify: `src/main/java/com/example/demoscope/ShortTermMemoryStore.java`
- Modify: `src/main/java/com/example/demoscope/InMemoryShortTermMemoryStore.java`

- [ ] **Step 1: Write the failing cross-user isolation test**

Add:

```java
@Test
void isolatesSameConversationIdByUserId() {
    ShortTermMemoryStore store = new InMemoryShortTermMemoryStore(3);
    MemoryTurn alice = turn("alice");
    MemoryTurn bob = turn("bob");

    store.append("user-a", "shared-conversation", alice);
    store.append("user-b", "shared-conversation", bob);

    assertEquals(List.of(alice), store.recent("user-a", "shared-conversation"));
    assertEquals(List.of(bob), store.recent("user-b", "shared-conversation"));
}
```

Update existing tests to call the desired two-ID API.

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=InMemoryShortTermMemoryStoreTest test
```

Expected: test compilation fails because the store methods do not accept
`userId`.

- [ ] **Step 3: Change the store contract**

Use:

```java
void append(String userId, String conversationId, MemoryTurn turn);

List<MemoryTurn> recent(String userId, String conversationId);
```

- [ ] **Step 4: Implement an immutable composite key**

Inside `InMemoryShortTermMemoryStore`, add:

```java
private record ConversationKey(String userId, String conversationId) {
}
```

Replace the map with:

```java
private final ConcurrentMap<ConversationKey, Deque<MemoryTurn>> turnsByConversation =
        new ConcurrentHashMap<>();
```

Construct `new ConversationKey(userId, conversationId)` in both `append` and
`recent`.

- [ ] **Step 5: Run store tests to verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=InMemoryShortTermMemoryStoreTest test
```

Expected: all store tests pass.

- [ ] **Step 6: Commit the composite memory scope**

```powershell
git add -- src/main/java/com/example/demoscope/ShortTermMemoryStore.java src/main/java/com/example/demoscope/InMemoryShortTermMemoryStore.java src/test/java/com/example/demoscope/InMemoryShortTermMemoryStoreTest.java
git commit -m "fix: isolate short-term memory by user"
```

### Task 5: Pass User Scope Through Memory Orchestration

**Files:**
- Modify: `src/test/java/com/example/demoscope/MemoryOrchestratorTest.java`
- Modify: `src/main/java/com/example/demoscope/MemoryOrchestrator.java`

- [ ] **Step 1: Write failing propagation assertions**

Update the test short-term store to capture both identifiers:

```java
assertEquals("user-42", shortTerm.lastUserId);
assertEquals("conversation-a", shortTerm.lastConversationId);
```

Assert propagation from both:

```java
orchestrator.prepare("user-42", "conversation-a", "question");
orchestrator.recordTurn("user-42", "conversation-a", "hello", "answer");
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=MemoryOrchestratorTest test
```

Expected: compilation or assertions fail because only `conversationId` reaches
short-term memory.

- [ ] **Step 3: Pass both identifiers**

Change:

```java
List<MemoryTurn> shortTerm = safelyReadShortTerm(userId, conversationId);
```

and:

```java
shortTermMemoryStore.append(userId, conversationId, turn);
```

Update `safelyReadShortTerm` to call:

```java
shortTermMemoryStore.recent(userId, conversationId);
```

Include both IDs in warning logs.

- [ ] **Step 4: Run orchestrator tests to verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=MemoryOrchestratorTest,OpenAiAgentChatServiceMemoryTest test
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit user-scope propagation**

```powershell
git add -- src/main/java/com/example/demoscope/MemoryOrchestrator.java src/test/java/com/example/demoscope/MemoryOrchestratorTest.java
git commit -m "fix: propagate user scope through memory orchestration"
```

### Task 6: Verify HTTP 401 and Application Integration

**Files:**
- Modify: `src/test/java/com/example/demoscope/AgentChatControllerTest.java`
- Modify: `src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java`
- Modify: `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`

- [ ] **Step 1: Strengthen controller identity tests**

Keep tests proving:

```java
// Valid parsed identity reaches the service.
assertEquals("user-42", agentChatService.lastUserId);

// Missing or invalid RuoYi token returns HTTP 401.
.andExpect(status().isUnauthorized());
```

Add a separate invalid-token case so missing and invalid tokens are documented
independently. Do not add role or permission checks to the controller or user
context.

- [ ] **Step 2: Prevent unrelated context tests from requiring live Redis**

In Spring context tests, provide a `@Primary` mocked `RedissonClient` or mocked
`SaTokenDao` test bean. The unit suite must not require Redis to be running.

- [ ] **Step 3: Run the focused authentication and memory suite**

Run:

```powershell
.\mvnw.cmd -Dtest=RedissonSaTokenDaoTest,RuoyiAuthConfigTest,DefaultSaTokenFacadeTest,RuoyiSaTokenUserContextTest,InMemoryShortTermMemoryStoreTest,MemoryOrchestratorTest,AgentChatControllerTest test
```

Expected: all selected tests pass with zero failures.

- [ ] **Step 4: Run the full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, zero failures, zero errors.

- [ ] **Step 5: Check the local Redis endpoint**

Run:

```powershell
redis-cli -h localhost -p 6379 ping
```

Expected: `PONG`. If `redis-cli` is unavailable, report that the automated tests
passed but the local Redis endpoint was not manually verified.

- [ ] **Step 6: Verify a real RuoYi token**

With RuoYi and this service pointing to the same Redis database, send:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/api/chat' `
  -Headers @{ Authorization = 'Bearer <RuoYi token>' } `
  -ContentType 'application/json' `
  -Body '{"conversationId":"ruoyi-smoke-test","message":"hello"}'
```

Expected: the request passes identity resolution rather than returning HTTP
401. Model configuration may still produce a separate server error if no model
API key is configured.

- [ ] **Step 7: Commit integration-test updates**

```powershell
git add -- src/test/java/com/example/demoscope/AgentChatControllerTest.java src/test/java/com/example/demoscope/AgentRuntimeConfigControllerTest.java src/test/java/com/example/demoscope/DemoScopeApplicationTests.java
git commit -m "test: cover ruoyi identity integration"
```

### Task 7: Final Review

**Files:**
- Review all modified files.

- [ ] **Step 1: Inspect the final diff**

Run:

```powershell
git diff --check
git diff --stat
git status --short
```

Expected: no whitespace errors; only intended source, test, configuration, and
documentation files are changed. Do not include `.codegraph/daemon.pid`.

- [ ] **Step 2: Re-run the full verification command**

Run:

```powershell
.\mvnw.cmd test
```

Expected: fresh `BUILD SUCCESS` output before any completion claim.
