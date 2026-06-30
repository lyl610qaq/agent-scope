# Package Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the flat `com.example.demoscope` Java package into feature-oriented packages and remove the old `agent-scope` RuoYi login proxy layer.

**Architecture:** Keep `DemoScopeApplication` at `com.example.demoscope` for Spring scanning. Move code into `agent`, `identity`, `interview`, `memory`, `knowledge`, and `llm` modules with light `api/application/domain/infrastructure/config` layering. Login happens directly in RuoYi; `agent-scope` only derives a trusted `userId` from the RuoYi token through Sa-Token and shared Redis.

**Tech Stack:** Java 17, Spring Boot 4 WebMVC, Maven Wrapper, Sa-Token, Redisson, JDBC, JUnit 5, Spring MockMvc.

---

## Current Constraints

- The workspace may already contain unrelated uncommitted changes in:
  - `src/main/java/com/example/demoscope/InterviewConfig.java`
  - `src/main/java/com/example/demoscope/InterviewController.java`
  - `src/main/java/com/example/demoscope/InterviewDatabaseConfig.java`
  - `src/main/resources/application.properties`
  - `src/test/java/com/example/demoscope/InterviewConfigTest.java`
  - `src/test/java/com/example/demoscope/InterviewDatabaseConfigTest.java`
  - `src/test/java/com/example/demoscope/ApplicationPropertiesTest.java`
- Preserve those edits while moving files. Do not revert or overwrite them.
- `DemoScopeApplication.java` stays at `src/main/java/com/example/demoscope/DemoScopeApplication.java`.
- The old `/api/auth/*` controller layer is intentionally removed.

## File Structure

### Production Files To Delete

- Delete: `src/main/java/com/example/demoscope/RuoyiAuthProxyController.java`
- Delete: `src/main/java/com/example/demoscope/RuoyiAuthProxyClient.java`
- Delete: `src/main/java/com/example/demoscope/RuoyiAuthProxyConfig.java`
- Delete: `src/main/java/com/example/demoscope/RuoyiAuthProxySettings.java`
- Delete: `src/main/java/com/example/demoscope/RuoyiAuthProxyResponse.java`
- Delete: `src/main/java/com/example/demoscope/RuoyiAuthProxyException.java`

### Test Files To Delete

- Delete: `src/test/java/com/example/demoscope/RuoyiAuthProxyControllerTest.java`
- Delete: `src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java`
- Delete: `src/test/java/com/example/demoscope/RuoyiAuthProxySettingsTest.java`
- Delete: `src/test/java/com/example/demoscope/RuoyiAuthFlowTest.java`

### Config To Modify

- Modify: `src/main/resources/application.properties`
  - Keep: `agentscope.auth.ruoyi.token-name`
  - Keep: `spring.data.redis.*`
  - Remove: `agentscope.auth.ruoyi.base-url`
  - Remove: `agentscope.auth.ruoyi.login-path`
  - Remove: `agentscope.auth.ruoyi.logout-path`
  - Remove: `agentscope.auth.ruoyi.connect-timeout`
  - Remove: `agentscope.auth.ruoyi.read-timeout`
  - Remove: `agentscope.auth.ruoyi.max-login-body-bytes`

### Production Package Targets

```text
src/main/java/com/example/demoscope/agent/api
- AgentChatController.java
- AgentRuntimeConfigController.java

src/main/java/com/example/demoscope/agent/application
- AgentChatService.java
- OpenAiAgentChatService.java

src/main/java/com/example/demoscope/agent/infrastructure
- AgentScopeChatTextModel.java

src/main/java/com/example/demoscope/identity/application
- AuthenticatedUserContext.java
- BearerTokenExtractor.java
- RuoyiSaTokenUserContext.java

src/main/java/com/example/demoscope/identity/domain
- SaTokenFacade.java
- UnauthenticatedUserException.java

src/main/java/com/example/demoscope/identity/infrastructure
- DefaultSaTokenFacade.java
- RedissonSaTokenDao.java

src/main/java/com/example/demoscope/identity/config
- RuoyiAuthConfig.java

src/main/java/com/example/demoscope/interview/api
- InterviewController.java

src/main/java/com/example/demoscope/interview/application
- InterviewService.java
- InterviewTranscriptRenderer.java
- InterviewMemoryContextProvider.java
- InterviewEvidenceProvider.java
- DefaultInterviewMemoryWriter.java

src/main/java/com/example/demoscope/interview/domain
- InterviewSession.java
- InterviewQuestion.java
- InterviewAnswer.java
- InterviewReport.java
- InterviewSnapshot.java
- InterviewRepository.java
- InterviewServiceException.java
- InterviewQuestionGenerator.java
- InterviewAnswerEvaluator.java
- InterviewReportGenerator.java
- InterviewMemoryWriter.java

src/main/java/com/example/demoscope/interview/agent
- InterviewAgentOrchestrator.java
- InterviewAgentName.java
- InterviewAgentTask.java
- InterviewAgentOutput.java
- InterviewRouterAgent.java
- InterviewRagPlannerAgent.java
- InterviewTargetAgent.java
- InterviewMemoryManagerAgent.java
- AgentPromptContext.java
- RouterDecision.java
- RagQueryPlan.java
- MemoryWriteDecision.java
- AgenticInterviewQuestionGenerator.java
- AgenticInterviewAnswerEvaluator.java
- AgenticInterviewReportGenerator.java
- ModelInterviewRouterAgent.java
- ModelInterviewRagPlannerAgent.java
- ModelInterviewQuestionGenerator.java
- ModelInterviewAnswerEvaluator.java
- ModelInterviewReportGenerator.java
- ModelInterviewMemoryManagerAgent.java
- ModelInterviewerAgent.java
- ModelJavaSkillAgent.java
- ModelProjectAgent.java
- ModelScoreAgent.java

src/main/java/com/example/demoscope/interview/infrastructure
- JdbcInterviewRepository.java
- InterviewAiJsonClient.java
- InterviewAiContracts.java

src/main/java/com/example/demoscope/interview/config
- InterviewConfig.java
- InterviewDatabaseConfig.java

src/main/java/com/example/demoscope/memory/application
- MemoryOrchestrator.java
- LongTermMemoryPolicy.java

src/main/java/com/example/demoscope/memory/domain
- MemoryContext.java
- MemoryTurn.java
- ShortTermMemoryStore.java
- LongTermMemory.java
- LongTermMemoryCandidate.java
- LongTermMemoryCategory.java
- LongTermMemoryRepository.java
- LongTermMemoryExtractor.java

src/main/java/com/example/demoscope/memory/infrastructure
- InMemoryShortTermMemoryStore.java
- EmptyLongTermMemoryRepository.java
- JsonLongTermMemoryRepository.java
- PostgresLongTermMemoryRepository.java
- PgVectorLongTermMemoryRepository.java
- ModelLongTermMemoryExtractor.java

src/main/java/com/example/demoscope/memory/config
- AgentMemoryConfig.java

src/main/java/com/example/demoscope/knowledge/application
- RagPromptBuilder.java
- PromptContextBuilder.java

src/main/java/com/example/demoscope/knowledge/domain
- KnowledgeChunk.java
- KnowledgeRetriever.java
- SemanticQuery.java
- RetrievalSettings.java
- EmbeddingClient.java

src/main/java/com/example/demoscope/knowledge/infrastructure
- LocalKnowledgeStore.java
- PgVectorKnowledgeStore.java
- SiliconFlowEmbeddingClient.java

src/main/java/com/example/demoscope/llm/domain
- ChatTextModel.java

src/main/java/com/example/demoscope/llm/infrastructure
- OpenAiRequestLogger.java
```

### Test Support Targets

```text
src/test/java/com/example/demoscope/testsupport
- TestRedissonConfig.java

src/test/java/com/example/demoscope/interview/support
- MutableInterviewRepository.java
```

Make `TestRedissonConfig` public because it is imported by tests outside its package.
Make `MutableInterviewRepository` public and make its helper methods used by tests public:

```text
final class MutableInterviewRepository implements InterviewRepository
-> public final class MutableInterviewRepository implements InterviewRepository

void seed(InterviewSnapshot snapshot)
-> public void seed(InterviewSnapshot snapshot)

void failNextMutation()
-> public void failNextMutation()

void winnerOnNextMutation(InterviewSnapshot winner)
-> public void winnerOnNextMutation(InterviewSnapshot winner)
```

## Task 1: Lock In Direct RuoYi Token Identity Behavior

**Files:**
- Move/modify: `src/test/java/com/example/demoscope/ApplicationPropertiesTest.java`
- New target: `src/test/java/com/example/demoscope/identity/config/ApplicationPropertiesTest.java`

- [ ] **Step 1: Write the failing configuration test**

Create or replace `src/test/java/com/example/demoscope/identity/config/ApplicationPropertiesTest.java` with:

```java
package com.example.demoscope.identity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ApplicationPropertiesTest {

    @Test
    void interviewsAreOptInByDefault() throws Exception {
        Properties properties = loadProperties();

        assertEquals(
                "${AGENTSCOPE_INTERVIEW_ENABLED:false}",
                properties.getProperty("agentscope.interview.enabled"));
    }

    @Test
    void ruoyiLoginIsDirectAndOnlyTokenIdentityConfigRemains()
            throws Exception {
        Properties properties = loadProperties();

        assertEquals(
                "${AGENTSCOPE_RUOYI_TOKEN_NAME:Authorization}",
                properties.getProperty("agentscope.auth.ruoyi.token-name"));
        assertEquals(
                "${AGENTSCOPE_RUOYI_REDIS_HOST:localhost}",
                properties.getProperty("spring.data.redis.host"));
        assertEquals(
                "${AGENTSCOPE_RUOYI_REDIS_PORT:6379}",
                properties.getProperty("spring.data.redis.port"));
        assertEquals(
                "${AGENTSCOPE_RUOYI_REDIS_DATABASE:0}",
                properties.getProperty("spring.data.redis.database"));

        assertFalse(properties.containsKey("agentscope.auth.ruoyi.base-url"));
        assertFalse(properties.containsKey("agentscope.auth.ruoyi.login-path"));
        assertFalse(properties.containsKey("agentscope.auth.ruoyi.logout-path"));
        assertFalse(properties.containsKey(
                "agentscope.auth.ruoyi.connect-timeout"));
        assertFalse(properties.containsKey(
                "agentscope.auth.ruoyi.read-timeout"));
        assertFalse(properties.containsKey(
                "agentscope.auth.ruoyi.max-login-body-bytes"));
    }

    private Properties loadProperties() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        return properties;
    }
}
```

- [ ] **Step 2: Remove the old root test file if it still exists**

Run:

```powershell
if (Test-Path 'src/test/java/com/example/demoscope/ApplicationPropertiesTest.java') {
    Remove-Item -LiteralPath 'src/test/java/com/example/demoscope/ApplicationPropertiesTest.java'
}
```

- [ ] **Step 3: Run the new test and verify it fails before implementation**

Run:

```powershell
.\mvnw.cmd "-Dtest=ApplicationPropertiesTest" test
```

Expected: FAIL because `application.properties` still contains at least one removed proxy property such as `agentscope.auth.ruoyi.base-url`.

- [ ] **Step 4: Commit**

Do not commit yet if Step 3 failed for the expected reason and the implementation is continuing immediately. Commit after Task 2 when the test passes.

## Task 2: Remove The Old RuoYi Login Proxy Layer

**Files:**
- Delete production proxy files listed in "Production Files To Delete"
- Delete proxy tests listed in "Test Files To Delete"
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`
- Modify: `src/test/java/com/example/demoscope/OpenAiModelConfigTest.java`

- [ ] **Step 1: Delete production proxy classes**

Run:

```powershell
$workspace = (Resolve-Path '.').Path
$deleteFiles = @(
    'src/main/java/com/example/demoscope/RuoyiAuthProxyController.java',
    'src/main/java/com/example/demoscope/RuoyiAuthProxyClient.java',
    'src/main/java/com/example/demoscope/RuoyiAuthProxyConfig.java',
    'src/main/java/com/example/demoscope/RuoyiAuthProxySettings.java',
    'src/main/java/com/example/demoscope/RuoyiAuthProxyResponse.java',
    'src/main/java/com/example/demoscope/RuoyiAuthProxyException.java',
    'src/test/java/com/example/demoscope/RuoyiAuthProxyControllerTest.java',
    'src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java',
    'src/test/java/com/example/demoscope/RuoyiAuthProxySettingsTest.java',
    'src/test/java/com/example/demoscope/RuoyiAuthFlowTest.java'
)
foreach ($relative in $deleteFiles) {
    $full = Join-Path $workspace $relative
    if (-not $full.StartsWith($workspace)) {
        throw "Refusing to delete outside workspace: $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full
    }
}
```

- [ ] **Step 2: Remove proxy properties**

Edit `src/main/resources/application.properties` so this block:

```properties
agentscope.auth.ruoyi.token-name=${AGENTSCOPE_RUOYI_TOKEN_NAME:Authorization}
agentscope.auth.ruoyi.base-url=${AGENTSCOPE_RUOYI_BASE_URL:}
agentscope.auth.ruoyi.login-path=${AGENTSCOPE_RUOYI_LOGIN_PATH:/auth/login}
agentscope.auth.ruoyi.logout-path=${AGENTSCOPE_RUOYI_LOGOUT_PATH:/auth/logout}
agentscope.auth.ruoyi.connect-timeout=${AGENTSCOPE_RUOYI_CONNECT_TIMEOUT:3s}
agentscope.auth.ruoyi.read-timeout=${AGENTSCOPE_RUOYI_READ_TIMEOUT:10s}
agentscope.auth.ruoyi.max-login-body-bytes=${AGENTSCOPE_RUOYI_MAX_LOGIN_BODY_BYTES:16384}
```

becomes:

```properties
agentscope.auth.ruoyi.token-name=${AGENTSCOPE_RUOYI_TOKEN_NAME:Authorization}
```

Do not edit the `spring.data.redis.*` lines in this task.

- [ ] **Step 3: Remove obsolete test properties**

In `src/test/java/com/example/demoscope/DemoScopeApplicationTests.java`, remove this entry from the `@SpringBootTest` property array:

```java
"agentscope.auth.ruoyi.base-url=http://127.0.0.1:18081",
```

In `src/test/java/com/example/demoscope/OpenAiModelConfigTest.java`, remove this entry from the `@SpringBootTest` property array:

```java
"agentscope.auth.ruoyi.base-url=http://127.0.0.1:18081",
```

- [ ] **Step 4: Run the direct identity config test**

Run:

```powershell
.\mvnw.cmd "-Dtest=ApplicationPropertiesTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- src/main/resources/application.properties `
    src/test/java/com/example/demoscope/identity/config/ApplicationPropertiesTest.java `
    src/test/java/com/example/demoscope/DemoScopeApplicationTests.java `
    src/test/java/com/example/demoscope/OpenAiModelConfigTest.java `
    src/main/java/com/example/demoscope/RuoyiAuthProxyController.java `
    src/main/java/com/example/demoscope/RuoyiAuthProxyClient.java `
    src/main/java/com/example/demoscope/RuoyiAuthProxyConfig.java `
    src/main/java/com/example/demoscope/RuoyiAuthProxySettings.java `
    src/main/java/com/example/demoscope/RuoyiAuthProxyResponse.java `
    src/main/java/com/example/demoscope/RuoyiAuthProxyException.java `
    src/test/java/com/example/demoscope/RuoyiAuthProxyControllerTest.java `
    src/test/java/com/example/demoscope/RuoyiAuthProxyClientTest.java `
    src/test/java/com/example/demoscope/RuoyiAuthProxySettingsTest.java `
    src/test/java/com/example/demoscope/RuoyiAuthFlowTest.java `
    src/test/java/com/example/demoscope/ApplicationPropertiesTest.java
git commit -m "refactor: remove RuoYi login proxy layer"
```

## Task 3: Move Production Packages

**Files:**
- Move all production files listed in "Production Package Targets"
- Modify package declarations and imports in `src/main/java/**/*.java`
- Modify imports in `src/test/java/**/*.java`

- [ ] **Step 1: Move production files and update package declarations**

Run this exact PowerShell script from the repository root:

```powershell
$workspace = (Resolve-Path '.').Path
$moves = [ordered]@{
    'AgentChatController.java' = 'agent/api'
    'AgentRuntimeConfigController.java' = 'agent/api'
    'AgentChatService.java' = 'agent/application'
    'OpenAiAgentChatService.java' = 'agent/application'
    'AgentScopeChatTextModel.java' = 'agent/infrastructure'

    'AuthenticatedUserContext.java' = 'identity/application'
    'BearerTokenExtractor.java' = 'identity/application'
    'RuoyiSaTokenUserContext.java' = 'identity/application'
    'SaTokenFacade.java' = 'identity/domain'
    'UnauthenticatedUserException.java' = 'identity/domain'
    'DefaultSaTokenFacade.java' = 'identity/infrastructure'
    'RedissonSaTokenDao.java' = 'identity/infrastructure'
    'RuoyiAuthConfig.java' = 'identity/config'

    'InterviewController.java' = 'interview/api'
    'InterviewService.java' = 'interview/application'
    'InterviewTranscriptRenderer.java' = 'interview/application'
    'InterviewMemoryContextProvider.java' = 'interview/application'
    'InterviewEvidenceProvider.java' = 'interview/application'
    'DefaultInterviewMemoryWriter.java' = 'interview/application'
    'InterviewSession.java' = 'interview/domain'
    'InterviewQuestion.java' = 'interview/domain'
    'InterviewAnswer.java' = 'interview/domain'
    'InterviewReport.java' = 'interview/domain'
    'InterviewSnapshot.java' = 'interview/domain'
    'InterviewRepository.java' = 'interview/domain'
    'InterviewServiceException.java' = 'interview/domain'
    'InterviewQuestionGenerator.java' = 'interview/domain'
    'InterviewAnswerEvaluator.java' = 'interview/domain'
    'InterviewReportGenerator.java' = 'interview/domain'
    'InterviewMemoryWriter.java' = 'interview/domain'
    'InterviewAgentOrchestrator.java' = 'interview/agent'
    'InterviewAgentName.java' = 'interview/agent'
    'InterviewAgentTask.java' = 'interview/agent'
    'InterviewAgentOutput.java' = 'interview/agent'
    'InterviewRouterAgent.java' = 'interview/agent'
    'InterviewRagPlannerAgent.java' = 'interview/agent'
    'InterviewTargetAgent.java' = 'interview/agent'
    'InterviewMemoryManagerAgent.java' = 'interview/agent'
    'AgentPromptContext.java' = 'interview/agent'
    'RouterDecision.java' = 'interview/agent'
    'RagQueryPlan.java' = 'interview/agent'
    'MemoryWriteDecision.java' = 'interview/agent'
    'AgenticInterviewQuestionGenerator.java' = 'interview/agent'
    'AgenticInterviewAnswerEvaluator.java' = 'interview/agent'
    'AgenticInterviewReportGenerator.java' = 'interview/agent'
    'ModelInterviewRouterAgent.java' = 'interview/agent'
    'ModelInterviewRagPlannerAgent.java' = 'interview/agent'
    'ModelInterviewQuestionGenerator.java' = 'interview/agent'
    'ModelInterviewAnswerEvaluator.java' = 'interview/agent'
    'ModelInterviewReportGenerator.java' = 'interview/agent'
    'ModelInterviewMemoryManagerAgent.java' = 'interview/agent'
    'ModelInterviewerAgent.java' = 'interview/agent'
    'ModelJavaSkillAgent.java' = 'interview/agent'
    'ModelProjectAgent.java' = 'interview/agent'
    'ModelScoreAgent.java' = 'interview/agent'
    'JdbcInterviewRepository.java' = 'interview/infrastructure'
    'InterviewAiJsonClient.java' = 'interview/infrastructure'
    'InterviewAiContracts.java' = 'interview/infrastructure'
    'InterviewConfig.java' = 'interview/config'
    'InterviewDatabaseConfig.java' = 'interview/config'

    'MemoryOrchestrator.java' = 'memory/application'
    'LongTermMemoryPolicy.java' = 'memory/application'
    'MemoryContext.java' = 'memory/domain'
    'MemoryTurn.java' = 'memory/domain'
    'ShortTermMemoryStore.java' = 'memory/domain'
    'LongTermMemory.java' = 'memory/domain'
    'LongTermMemoryCandidate.java' = 'memory/domain'
    'LongTermMemoryCategory.java' = 'memory/domain'
    'LongTermMemoryRepository.java' = 'memory/domain'
    'LongTermMemoryExtractor.java' = 'memory/domain'
    'InMemoryShortTermMemoryStore.java' = 'memory/infrastructure'
    'EmptyLongTermMemoryRepository.java' = 'memory/infrastructure'
    'JsonLongTermMemoryRepository.java' = 'memory/infrastructure'
    'PostgresLongTermMemoryRepository.java' = 'memory/infrastructure'
    'PgVectorLongTermMemoryRepository.java' = 'memory/infrastructure'
    'ModelLongTermMemoryExtractor.java' = 'memory/infrastructure'
    'AgentMemoryConfig.java' = 'memory/config'

    'RagPromptBuilder.java' = 'knowledge/application'
    'PromptContextBuilder.java' = 'knowledge/application'
    'KnowledgeChunk.java' = 'knowledge/domain'
    'KnowledgeRetriever.java' = 'knowledge/domain'
    'SemanticQuery.java' = 'knowledge/domain'
    'RetrievalSettings.java' = 'knowledge/domain'
    'EmbeddingClient.java' = 'knowledge/domain'
    'LocalKnowledgeStore.java' = 'knowledge/infrastructure'
    'PgVectorKnowledgeStore.java' = 'knowledge/infrastructure'
    'SiliconFlowEmbeddingClient.java' = 'knowledge/infrastructure'

    'ChatTextModel.java' = 'llm/domain'
    'OpenAiRequestLogger.java' = 'llm/infrastructure'
}

foreach ($entry in $moves.GetEnumerator()) {
    $fileName = $entry.Key
    $targetDir = $entry.Value
    $source = Join-Path $workspace "src/main/java/com/example/demoscope/$fileName"
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing source file: $source"
    }
    $destinationDirectory = Join-Path $workspace "src/main/java/com/example/demoscope/$targetDir"
    $destination = Join-Path $destinationDirectory $fileName
    if (-not $destination.StartsWith($workspace)) {
        throw "Refusing move outside workspace: $destination"
    }
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    Move-Item -LiteralPath $source -Destination $destination
    $package = 'com.example.demoscope.' + ($targetDir -replace '/', '.')
    $content = Get-Content -Raw -LiteralPath $destination
    $content = $content -replace '^package com\.example\.demoscope;', "package $package;"
    Set-Content -NoNewline -LiteralPath $destination -Value $content
}
```

- [ ] **Step 2: Add imports for moved production symbols**

Run this exact PowerShell script from the repository root:

```powershell
$workspace = (Resolve-Path '.').Path
$symbolPackages = [ordered]@{
    'AgentChatController' = 'com.example.demoscope.agent.api.AgentChatController'
    'AgentRuntimeConfigController' = 'com.example.demoscope.agent.api.AgentRuntimeConfigController'
    'AgentChatService' = 'com.example.demoscope.agent.application.AgentChatService'
    'OpenAiAgentChatService' = 'com.example.demoscope.agent.application.OpenAiAgentChatService'
    'AgentScopeChatTextModel' = 'com.example.demoscope.agent.infrastructure.AgentScopeChatTextModel'
    'AuthenticatedUserContext' = 'com.example.demoscope.identity.application.AuthenticatedUserContext'
    'BearerTokenExtractor' = 'com.example.demoscope.identity.application.BearerTokenExtractor'
    'RuoyiSaTokenUserContext' = 'com.example.demoscope.identity.application.RuoyiSaTokenUserContext'
    'SaTokenFacade' = 'com.example.demoscope.identity.domain.SaTokenFacade'
    'UnauthenticatedUserException' = 'com.example.demoscope.identity.domain.UnauthenticatedUserException'
    'DefaultSaTokenFacade' = 'com.example.demoscope.identity.infrastructure.DefaultSaTokenFacade'
    'RedissonSaTokenDao' = 'com.example.demoscope.identity.infrastructure.RedissonSaTokenDao'
    'RuoyiAuthConfig' = 'com.example.demoscope.identity.config.RuoyiAuthConfig'
    'InterviewController' = 'com.example.demoscope.interview.api.InterviewController'
    'InterviewService' = 'com.example.demoscope.interview.application.InterviewService'
    'InterviewTranscriptRenderer' = 'com.example.demoscope.interview.application.InterviewTranscriptRenderer'
    'InterviewMemoryContextProvider' = 'com.example.demoscope.interview.application.InterviewMemoryContextProvider'
    'InterviewEvidenceProvider' = 'com.example.demoscope.interview.application.InterviewEvidenceProvider'
    'DefaultInterviewMemoryWriter' = 'com.example.demoscope.interview.application.DefaultInterviewMemoryWriter'
    'InterviewSession' = 'com.example.demoscope.interview.domain.InterviewSession'
    'InterviewQuestion' = 'com.example.demoscope.interview.domain.InterviewQuestion'
    'InterviewAnswer' = 'com.example.demoscope.interview.domain.InterviewAnswer'
    'InterviewReport' = 'com.example.demoscope.interview.domain.InterviewReport'
    'InterviewSnapshot' = 'com.example.demoscope.interview.domain.InterviewSnapshot'
    'InterviewRepository' = 'com.example.demoscope.interview.domain.InterviewRepository'
    'InterviewServiceException' = 'com.example.demoscope.interview.domain.InterviewServiceException'
    'InterviewQuestionGenerator' = 'com.example.demoscope.interview.domain.InterviewQuestionGenerator'
    'InterviewAnswerEvaluator' = 'com.example.demoscope.interview.domain.InterviewAnswerEvaluator'
    'InterviewReportGenerator' = 'com.example.demoscope.interview.domain.InterviewReportGenerator'
    'InterviewMemoryWriter' = 'com.example.demoscope.interview.domain.InterviewMemoryWriter'
    'InterviewAgentOrchestrator' = 'com.example.demoscope.interview.agent.InterviewAgentOrchestrator'
    'InterviewAgentName' = 'com.example.demoscope.interview.agent.InterviewAgentName'
    'InterviewAgentTask' = 'com.example.demoscope.interview.agent.InterviewAgentTask'
    'InterviewAgentOutput' = 'com.example.demoscope.interview.agent.InterviewAgentOutput'
    'InterviewRouterAgent' = 'com.example.demoscope.interview.agent.InterviewRouterAgent'
    'InterviewRagPlannerAgent' = 'com.example.demoscope.interview.agent.InterviewRagPlannerAgent'
    'InterviewTargetAgent' = 'com.example.demoscope.interview.agent.InterviewTargetAgent'
    'InterviewMemoryManagerAgent' = 'com.example.demoscope.interview.agent.InterviewMemoryManagerAgent'
    'AgentPromptContext' = 'com.example.demoscope.interview.agent.AgentPromptContext'
    'RouterDecision' = 'com.example.demoscope.interview.agent.RouterDecision'
    'RagQueryPlan' = 'com.example.demoscope.interview.agent.RagQueryPlan'
    'MemoryWriteDecision' = 'com.example.demoscope.interview.agent.MemoryWriteDecision'
    'AgenticInterviewQuestionGenerator' = 'com.example.demoscope.interview.agent.AgenticInterviewQuestionGenerator'
    'AgenticInterviewAnswerEvaluator' = 'com.example.demoscope.interview.agent.AgenticInterviewAnswerEvaluator'
    'AgenticInterviewReportGenerator' = 'com.example.demoscope.interview.agent.AgenticInterviewReportGenerator'
    'ModelInterviewRouterAgent' = 'com.example.demoscope.interview.agent.ModelInterviewRouterAgent'
    'ModelInterviewRagPlannerAgent' = 'com.example.demoscope.interview.agent.ModelInterviewRagPlannerAgent'
    'ModelInterviewQuestionGenerator' = 'com.example.demoscope.interview.agent.ModelInterviewQuestionGenerator'
    'ModelInterviewAnswerEvaluator' = 'com.example.demoscope.interview.agent.ModelInterviewAnswerEvaluator'
    'ModelInterviewReportGenerator' = 'com.example.demoscope.interview.agent.ModelInterviewReportGenerator'
    'ModelInterviewMemoryManagerAgent' = 'com.example.demoscope.interview.agent.ModelInterviewMemoryManagerAgent'
    'ModelInterviewerAgent' = 'com.example.demoscope.interview.agent.ModelInterviewerAgent'
    'ModelJavaSkillAgent' = 'com.example.demoscope.interview.agent.ModelJavaSkillAgent'
    'ModelProjectAgent' = 'com.example.demoscope.interview.agent.ModelProjectAgent'
    'ModelScoreAgent' = 'com.example.demoscope.interview.agent.ModelScoreAgent'
    'JdbcInterviewRepository' = 'com.example.demoscope.interview.infrastructure.JdbcInterviewRepository'
    'InterviewAiJsonClient' = 'com.example.demoscope.interview.infrastructure.InterviewAiJsonClient'
    'InterviewAiContracts' = 'com.example.demoscope.interview.infrastructure.InterviewAiContracts'
    'InterviewConfig' = 'com.example.demoscope.interview.config.InterviewConfig'
    'InterviewDatabaseConfig' = 'com.example.demoscope.interview.config.InterviewDatabaseConfig'
    'MemoryOrchestrator' = 'com.example.demoscope.memory.application.MemoryOrchestrator'
    'LongTermMemoryPolicy' = 'com.example.demoscope.memory.application.LongTermMemoryPolicy'
    'MemoryContext' = 'com.example.demoscope.memory.domain.MemoryContext'
    'MemoryTurn' = 'com.example.demoscope.memory.domain.MemoryTurn'
    'ShortTermMemoryStore' = 'com.example.demoscope.memory.domain.ShortTermMemoryStore'
    'LongTermMemory' = 'com.example.demoscope.memory.domain.LongTermMemory'
    'LongTermMemoryCandidate' = 'com.example.demoscope.memory.domain.LongTermMemoryCandidate'
    'LongTermMemoryCategory' = 'com.example.demoscope.memory.domain.LongTermMemoryCategory'
    'LongTermMemoryRepository' = 'com.example.demoscope.memory.domain.LongTermMemoryRepository'
    'LongTermMemoryExtractor' = 'com.example.demoscope.memory.domain.LongTermMemoryExtractor'
    'InMemoryShortTermMemoryStore' = 'com.example.demoscope.memory.infrastructure.InMemoryShortTermMemoryStore'
    'EmptyLongTermMemoryRepository' = 'com.example.demoscope.memory.infrastructure.EmptyLongTermMemoryRepository'
    'JsonLongTermMemoryRepository' = 'com.example.demoscope.memory.infrastructure.JsonLongTermMemoryRepository'
    'PostgresLongTermMemoryRepository' = 'com.example.demoscope.memory.infrastructure.PostgresLongTermMemoryRepository'
    'PgVectorLongTermMemoryRepository' = 'com.example.demoscope.memory.infrastructure.PgVectorLongTermMemoryRepository'
    'ModelLongTermMemoryExtractor' = 'com.example.demoscope.memory.infrastructure.ModelLongTermMemoryExtractor'
    'AgentMemoryConfig' = 'com.example.demoscope.memory.config.AgentMemoryConfig'
    'RagPromptBuilder' = 'com.example.demoscope.knowledge.application.RagPromptBuilder'
    'PromptContextBuilder' = 'com.example.demoscope.knowledge.application.PromptContextBuilder'
    'KnowledgeChunk' = 'com.example.demoscope.knowledge.domain.KnowledgeChunk'
    'KnowledgeRetriever' = 'com.example.demoscope.knowledge.domain.KnowledgeRetriever'
    'SemanticQuery' = 'com.example.demoscope.knowledge.domain.SemanticQuery'
    'RetrievalSettings' = 'com.example.demoscope.knowledge.domain.RetrievalSettings'
    'EmbeddingClient' = 'com.example.demoscope.knowledge.domain.EmbeddingClient'
    'LocalKnowledgeStore' = 'com.example.demoscope.knowledge.infrastructure.LocalKnowledgeStore'
    'PgVectorKnowledgeStore' = 'com.example.demoscope.knowledge.infrastructure.PgVectorKnowledgeStore'
    'SiliconFlowEmbeddingClient' = 'com.example.demoscope.knowledge.infrastructure.SiliconFlowEmbeddingClient'
    'ChatTextModel' = 'com.example.demoscope.llm.domain.ChatTextModel'
    'OpenAiRequestLogger' = 'com.example.demoscope.llm.infrastructure.OpenAiRequestLogger'
}

function Get-PackageName($content) {
    $match = [regex]::Match($content, '(?m)^package\s+([^;]+);')
    if (-not $match.Success) { return '' }
    return $match.Groups[1].Value
}

function Get-PrimaryTypeName($path) {
    return [System.IO.Path]::GetFileNameWithoutExtension($path)
}

function Add-Imports($path) {
    $content = Get-Content -Raw -LiteralPath $path
    $package = Get-PackageName $content
    $primaryType = Get-PrimaryTypeName $path
    $imports = New-Object System.Collections.Generic.List[string]
    foreach ($entry in $symbolPackages.GetEnumerator()) {
        $symbol = $entry.Key
        $fqcn = $entry.Value
        $symbolPackage = $fqcn.Substring(0, $fqcn.LastIndexOf('.'))
        if ($symbol -eq $primaryType) { continue }
        if ($symbolPackage -eq $package) { continue }
        if ($content -notmatch "\b$([regex]::Escape($symbol))\b") { continue }
        if ($content -match "(?m)^import\s+$([regex]::Escape($fqcn));") { continue }
        $imports.Add("import $fqcn;")
    }
    if ($imports.Count -eq 0) { return }

    $uniqueImports = $imports | Sort-Object -Unique
    $importBlock = ($uniqueImports -join [Environment]::NewLine) + [Environment]::NewLine
    if ($content -match '(?m)^import\s+') {
        $content = [regex]::Replace(
            $content,
            '(?m)^(import\s+[^;]+;\r?\n)',
            $importBlock + '$1',
            1)
    } else {
        $content = [regex]::Replace(
            $content,
            '(?m)^(package\s+[^;]+;\r?\n)',
            '$1' + [Environment]::NewLine + $importBlock,
            1)
    }
    Set-Content -NoNewline -LiteralPath $path -Value $content
}

Get-ChildItem -Path 'src/main/java','src/test/java' -Recurse -Filter '*.java' |
    ForEach-Object { Add-Imports $_.FullName }
```

- [ ] **Step 3: Compile production sources**

Run:

```powershell
.\mvnw.cmd "-DskipTests" test
```

Expected: PASS. On compiler failure, stop and inspect the named missing symbols, add imports for those symbols, and rerun this command before continuing.

- [ ] **Step 4: Commit**

Run:

```powershell
git add -- src/main/java src/test/java
git commit -m "refactor: move production code into feature packages"
```

## Task 4: Move And Repair Test Packages

**Files:**
- Move test files under `src/test/java/com/example/demoscope/**`
- Modify package declarations and helper visibility
- Modify imports in tests

- [ ] **Step 1: Move test files into feature packages**

Use this mapping:

```text
agent/api:
- AgentChatControllerTest.java
- AgentRuntimeConfigControllerTest.java
- AgentChatRuoyiAuthTest.java

agent/application:
- OpenAiAgentChatServiceMemoryTest.java

agent/infrastructure:
- AgentScopeChatTextModelTest.java

identity/application:
- RuoyiSaTokenUserContextTest.java

identity/config:
- RuoyiAuthConfigTest.java
- ApplicationPropertiesTest.java

identity/infrastructure:
- DefaultSaTokenFacadeTest.java
- RedissonSaTokenDaoTest.java

testsupport:
- TestRedissonConfig.java

knowledge/application:
- RagPromptBuilderTest.java
- PromptContextBuilderTest.java

knowledge/domain:
- SemanticQueryTest.java
- RetrievalSettingsTest.java

knowledge/infrastructure:
- SiliconFlowEmbeddingClientTest.java
- PgVectorKnowledgeStoreTest.java
- LocalKnowledgeStoreTest.java

llm/infrastructure:
- OpenAiRequestLoggerTest.java
- OpenAiModelConfigTest.java

memory/application:
- MemoryOrchestratorTest.java
- LongTermMemoryPolicyTest.java

memory/infrastructure:
- InMemoryShortTermMemoryStoreTest.java
- JsonLongTermMemoryRepositoryTest.java
- PostgresLongTermMemoryRepositoryTest.java
- PgVectorLongTermMemoryRepositoryTest.java
- ModelLongTermMemoryExtractorTest.java

interview/api:
- InterviewControllerTest.java
- AuthenticatedInterviewFlowTest.java
- AgenticAuthenticatedInterviewFlowTest.java

interview/application:
- InterviewServiceCreationTest.java
- InterviewServiceAnswerTest.java
- InterviewServiceFinishTest.java
- InterviewMemorySupportTest.java
- InterviewEvidenceProviderPlanTest.java

interview/domain:
- InterviewDomainTest.java

interview/agent:
- InterviewAgentOrchestratorTest.java
- InterviewAgentContractsTest.java
- ModelInterviewAgentTest.java
- ModelInterviewAiTest.java

interview/infrastructure:
- JdbcInterviewRepositoryTest.java
- InterviewAiJsonClientTest.java

interview/config:
- InterviewConfigTest.java
- InterviewDatabaseConfigTest.java

interview/support:
- MutableInterviewRepository.java

root package:
- DemoScopeApplicationTests.java
```

Move the files with PowerShell `Move-Item`, create destination directories first, and update each `package` line to match the destination path. Keep `DemoScopeApplicationTests.java` in `com.example.demoscope`.

- [ ] **Step 2: Make test support classes public**

Update `src/test/java/com/example/demoscope/testsupport/TestRedissonConfig.java`:

```java
package com.example.demoscope.testsupport;

import static org.mockito.Mockito.mock;

import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestRedissonConfig {

    @Bean
    RedissonClient testRedissonClient() {
        return mock(RedissonClient.class);
    }
}
```

Update `src/test/java/com/example/demoscope/interview/support/MutableInterviewRepository.java` with these exact textual replacements:

```powershell
$path = 'src/test/java/com/example/demoscope/interview/support/MutableInterviewRepository.java'
$content = Get-Content -Raw -LiteralPath $path
$content = $content.Replace(
    'package com.example.demoscope;',
    'package com.example.demoscope.interview.support;')
$content = $content.Replace(
    'final class MutableInterviewRepository implements InterviewRepository',
    'public final class MutableInterviewRepository implements InterviewRepository')
$content = $content.Replace(
    '    void seed(InterviewSnapshot snapshot)',
    '    public void seed(InterviewSnapshot snapshot)')
$content = $content.Replace(
    '    void failNextMutation()',
    '    public void failNextMutation()')
$content = $content.Replace(
    '    void winnerOnNextMutation(InterviewSnapshot winner)',
    '    public void winnerOnNextMutation(InterviewSnapshot winner)')
Set-Content -NoNewline -LiteralPath $path -Value $content
```

- [ ] **Step 3: Update imports for moved test support**

Any test using `@Import(TestRedissonConfig.class)` must import:

```java
import com.example.demoscope.testsupport.TestRedissonConfig;
```

Any interview test using `MutableInterviewRepository` must import:

```java
import com.example.demoscope.interview.support.MutableInterviewRepository;
```

- [ ] **Step 4: Compile all tests**

Run:

```powershell
.\mvnw.cmd "-DskipTests" test
```

Expected: PASS. On package-private access failure, place the failing test in the same package as the class under test when it is testing package-private production behavior; use public visibility only for shared test support classes.

- [ ] **Step 5: Commit**

Run:

```powershell
git add -- src/test/java
git commit -m "refactor: move tests into feature packages"
```

## Task 5: Run Focused Test Suites

**Files:**
- No planned source edits.

- [ ] **Step 1: Run identity tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=RuoyiAuthConfigTest,RuoyiSaTokenUserContextTest,DefaultSaTokenFacadeTest,RedissonSaTokenDaoTest,ApplicationPropertiesTest" test
```

Expected: PASS.

- [ ] **Step 2: Run agent and identity flow tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=AgentChatControllerTest,AgentRuntimeConfigControllerTest,AgentChatRuoyiAuthTest,OpenAiAgentChatServiceMemoryTest" test
```

Expected: PASS. This proves business APIs still derive `userId` from token-backed `AuthenticatedUserContext`.

- [ ] **Step 3: Run knowledge, memory, and LLM tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=SemanticQueryTest,RetrievalSettingsTest,RagPromptBuilderTest,PromptContextBuilderTest,LocalKnowledgeStoreTest,PgVectorKnowledgeStoreTest,MemoryOrchestratorTest,LongTermMemoryPolicyTest,InMemoryShortTermMemoryStoreTest,OpenAiRequestLoggerTest" test
```

Expected: PASS.

- [ ] **Step 4: Run interview tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=InterviewConfigTest,InterviewDatabaseConfigTest,InterviewDomainTest,InterviewControllerTest,InterviewServiceCreationTest,InterviewServiceAnswerTest,InterviewServiceFinishTest,InterviewAgentOrchestratorTest,AuthenticatedInterviewFlowTest,AgenticAuthenticatedInterviewFlowTest" test
```

Expected: PASS.

- [ ] **Step 5: Commit package repair changes**

If Steps 1-4 required import or package fixes, commit them:

```powershell
git add -- src/main/java src/test/java
git commit -m "fix: repair package migration imports"
```

If no files changed, skip this commit.

## Task 6: Full Verification

**Files:**
- No planned source edits.

- [ ] **Step 1: Run the full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS.

- [ ] **Step 2: Inspect remaining root package files**

Run:

```powershell
rg --files src/main/java/com/example/demoscope
```

Expected: Only `DemoScopeApplication.java` appears directly under `src/main/java/com/example/demoscope`; all other production files are in subdirectories.

- [ ] **Step 3: Confirm no old auth proxy code remains**

Run:

```powershell
rg --line-number "RuoyiAuthProxy|/api/auth|AGENTSCOPE_RUOYI_BASE_URL|agentscope.auth.ruoyi.base-url" src/main src/test
```

Expected: no matches.

- [ ] **Step 4: Inspect git status**

Run:

```powershell
git status --short
```

Expected: only intentional package reorganization changes are present.

- [ ] **Step 5: Final commit if needed**

If Task 6 produced final cleanup changes, run:

```powershell
git add -- src/main/java src/test/java src/main/resources/application.properties
git commit -m "test: verify package reorganization"
```

If no files changed, skip this commit.

## Self-Review Notes

- Spec coverage:
  - Feature package tree is covered by Tasks 3 and 4.
  - Direct RuoYi login and token-derived user identity are covered by Tasks 1 and 2.
  - Old `/api/auth/*` proxy removal is covered by Tasks 2 and 6.
  - Spring scanning is protected by keeping `DemoScopeApplication` in the root package and running `DemoScopeApplicationTests`.
  - Focused and full verification are covered by Tasks 5 and 6.
- Placeholder scan:
  - No placeholder marker terms remain.
- Type/path consistency:
  - `AuthenticatedUserContext` remains the business-facing identity port.
  - `RuoyiAuthConfig` remains the Sa-Token and Redisson configuration class.
  - `DemoScopeApplication` remains in the root package.
