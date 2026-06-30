# Package Reorganization Design

Date: 2026-06-22

## Goal

Reorganize the current flat `com.example.demoscope` Java package into
feature-oriented modules with light internal layering. The change should make
the code easier to navigate by business capability while preserving the
interview, chat, memory, knowledge, Spring bean wiring, and test behavior.

One intentional behavior change is included: `agent-scope` should no longer
expose its own authentication endpoints. Clients log in through RuoYi directly
and pass the RuoYi token to `agent-scope` business APIs.

This design also confirms that login remains a RuoYi-Cloud-Plus responsibility.
Clients log in through RuoYi directly, then call this application with the
RuoYi token. This application only resolves a trusted `userId` from that token
through RuoYi/Sa-Token login state.

## Current State

All production classes currently live directly under:

```text
src/main/java/com/example/demoscope
```

The package contains several distinct responsibilities:

- RuoYi token parsing and Sa-Token user lookup.
- Generic agent chat APIs and model-backed chat service.
- Authenticated Java interview APIs, state machine, persistence, and AI
  adapters.
- Interview multi-agent orchestration.
- Short-term and long-term memory.
- Knowledge retrieval, embeddings, RAG prompt support, and pgvector stores.
- Model infrastructure such as chat model abstraction and request logging.

The flat package makes these boundaries harder to see and increases import
noise as the application grows.

## Chosen Approach

Use business-module-first packaging with a small common vocabulary inside each
module:

```text
api
application
domain
infrastructure
config
```

The module comes first because this codebase is shaped around business
capabilities, not around a single shared technical layer. For example,
`interview.api.InterviewController` and
`interview.application.InterviewService` are easier to reason about together
than a global `controller` package containing every endpoint.

The package root remains:

```text
com.example.demoscope
```

`DemoScopeApplication` stays at the root so Spring component scanning continues
to include every subpackage.

## Target Package Tree

```text
com.example.demoscope
|-- DemoScopeApplication
|-- agent
|   |-- api
|   |-- application
|   `-- infrastructure
|-- identity
|   |-- application
|   |-- domain
|   |-- infrastructure
|   `-- config
|-- interview
|   |-- api
|   |-- application
|   |-- domain
|   |-- agent
|   |-- infrastructure
|   `-- config
|-- memory
|   |-- application
|   |-- domain
|   |-- infrastructure
|   `-- config
|-- knowledge
|   |-- application
|   |-- domain
|   `-- infrastructure
`-- llm
    |-- domain
    `-- infrastructure
```

## Module Boundaries

### agent

Generic chat and runtime configuration for the general assistant route.

```text
agent.api
- AgentChatController
- AgentRuntimeConfigController

agent.application
- AgentChatService
- OpenAiAgentChatService

agent.infrastructure
- AgentScopeChatTextModel
```

The generic chat module may depend on `identity`, `memory`, `knowledge`, and
`llm`. It should not depend on interview-specific state-machine classes.

### identity

Identity integration with RuoYi-Cloud-Plus and Sa-Token.

```text
identity.application
- AuthenticatedUserContext
- BearerTokenExtractor
- RuoyiSaTokenUserContext

identity.domain
- SaTokenFacade
- UnauthenticatedUserException

identity.infrastructure
- DefaultSaTokenFacade
- RedissonSaTokenDao

identity.config
- RuoyiAuthConfig
```

The identity module does not expose `/api/auth/*` endpoints. It exists only to
turn a RuoYi token into a trusted application `userId`.

Runtime flow:

```text
client
  -> RuoYi Gateway /auth/login
  -> receives RuoYi access_token
  -> calls agent-scope APIs with Authorization: Bearer <access_token>
  -> agent-scope resolves userId from RuoYi/Sa-Token state
```

Business APIs must not trust a client-supplied `userId`. They call
`AuthenticatedUserContext`, which reads the configured token header, default
`Authorization`, accepts both `Bearer <token>` and raw token values, and
resolves the login id through Sa-Token backed by the shared RuoYi Redis store.

Required runtime configuration:

```properties
AGENTSCOPE_RUOYI_TOKEN_NAME=Authorization
AGENTSCOPE_RUOYI_REDIS_HOST=localhost
AGENTSCOPE_RUOYI_REDIS_PORT=6379
AGENTSCOPE_RUOYI_REDIS_DATABASE=0
```

Removed from the target architecture:

```text
- RuoyiAuthProxyController
- RuoyiAuthProxyClient
- RuoyiAuthProxyConfig
- RuoyiAuthProxySettings
- RuoyiAuthProxyResponse
- RuoyiAuthProxyException
```

Those classes belong to the previous proxy-login approach. With direct RuoYi
login, they should be removed during implementation.

### interview

Authenticated Java technical interview business workflow.

```text
interview.api
- InterviewController

interview.application
- InterviewService
- InterviewTranscriptRenderer
- InterviewMemoryContextProvider
- InterviewEvidenceProvider
- DefaultInterviewMemoryWriter

interview.domain
- InterviewSession
- InterviewQuestion
- InterviewAnswer
- InterviewReport
- InterviewSnapshot
- InterviewRepository
- InterviewServiceException
- InterviewQuestionGenerator
- InterviewAnswerEvaluator
- InterviewReportGenerator
- InterviewMemoryWriter

interview.agent
- InterviewAgentOrchestrator
- InterviewAgentName
- InterviewAgentTask
- InterviewAgentOutput
- InterviewRouterAgent
- InterviewRagPlannerAgent
- InterviewTargetAgent
- InterviewMemoryManagerAgent
- AgentPromptContext
- RouterDecision
- RagQueryPlan
- MemoryWriteDecision
- AgenticInterviewQuestionGenerator
- AgenticInterviewAnswerEvaluator
- AgenticInterviewReportGenerator
- ModelInterviewRouterAgent
- ModelInterviewRagPlannerAgent
- ModelInterviewQuestionGenerator
- ModelInterviewAnswerEvaluator
- ModelInterviewReportGenerator
- ModelInterviewMemoryManagerAgent
- ModelInterviewerAgent
- ModelJavaSkillAgent
- ModelProjectAgent
- ModelScoreAgent

interview.infrastructure
- JdbcInterviewRepository
- InterviewAiJsonClient
- InterviewAiContracts

interview.config
- InterviewConfig
- InterviewDatabaseConfig
```

`interview.application` owns lifecycle orchestration and business rules.
`interview.domain` owns state, ports, and exceptions. `interview.agent` is
kept under the interview module because its router, planner, target agents,
and memory manager are specific to the interview workflow.

### memory

Short-term and long-term memory abstractions and storage implementations.

```text
memory.application
- MemoryOrchestrator
- LongTermMemoryPolicy

memory.domain
- MemoryContext
- MemoryTurn
- ShortTermMemoryStore
- LongTermMemory
- LongTermMemoryCandidate
- LongTermMemoryCategory
- LongTermMemoryRepository
- LongTermMemoryExtractor

memory.infrastructure
- InMemoryShortTermMemoryStore
- EmptyLongTermMemoryRepository
- JsonLongTermMemoryRepository
- PostgresLongTermMemoryRepository
- PgVectorLongTermMemoryRepository
- ModelLongTermMemoryExtractor

memory.config
- AgentMemoryConfig
```

Memory remains reusable by both generic chat and interview orchestration.

### knowledge

Knowledge retrieval, embeddings, and RAG prompt support.

```text
knowledge.application
- RagPromptBuilder
- PromptContextBuilder

knowledge.domain
- KnowledgeChunk
- KnowledgeRetriever
- SemanticQuery
- RetrievalSettings
- EmbeddingClient

knowledge.infrastructure
- LocalKnowledgeStore
- PgVectorKnowledgeStore
- SiliconFlowEmbeddingClient
```

Knowledge retrieval remains reusable by chat, memory, and interview agents.

### llm

Shared model abstractions and low-level model infrastructure.

```text
llm.domain
- ChatTextModel

llm.infrastructure
- OpenAiRequestLogger
```

The model logging component remains separate from business modules because it
is a technical concern shared by multiple model-backed adapters.

## Alternatives Considered

### Global technical layers

One option is:

```text
controller
service
repository
domain
config
```

This is easy to migrate but scales poorly for this project because unrelated
business workflows end up adjacent again.

### Strict DDD per bounded context

Another option is to use heavier names such as `interfaces`, `application`,
`domain`, and `infrastructure` for every context, with strict dependency rules
and anti-corruption layers.

This is clean, but heavier than the current codebase needs. The selected
approach keeps the important boundaries without forcing a large architectural
rewrite.

## Migration Strategy

The package reorganization is mostly a mechanical move plus import/package
repair. The only intended behavior change is removing the old `agent-scope`
authentication endpoint layer.

Steps:

1. Create the target package directories under `src/main/java`.
2. Move production Java files to the package paths described above.
3. Update each `package` declaration.
4. Update imports across production and test code.
5. Mirror the package structure under `src/test/java`.
6. Keep `DemoScopeApplication` in the root package.
7. Run the existing focused tests first, then the full Maven test suite if the
   environment permits.

The implementation must preserve existing uncommitted user changes. If a file
with user edits is moved, the move should carry those edits forward instead of
reverting them.

## Test Strategy

Focused verification after migration:

- Identity tests:
  - `RuoyiAuthConfigTest`
  - `RuoyiSaTokenUserContextTest`
  - `DefaultSaTokenFacadeTest`
- Interview tests:
  - `InterviewConfigTest`
  - `InterviewDatabaseConfigTest`
  - `InterviewControllerTest`
  - `InterviewServiceCreationTest`
  - `InterviewServiceAnswerTest`
  - `InterviewServiceFinishTest`
  - `InterviewAgentOrchestratorTest`
- Memory, knowledge, and model tests:
  - `MemoryOrchestratorTest`
  - `InMemoryShortTermMemoryStoreTest`
  - `PgVectorKnowledgeStoreTest`
  - `RagPromptBuilderTest`
  - `OpenAiAgentChatServiceMemoryTest`

Final verification:

```text
mvn test
```

If full tests require unavailable external services, report the exact failing
tests and the missing dependency.

## Acceptance Criteria

- Production code no longer sits in one flat `com.example.demoscope` package.
- Business capabilities are visible from the package tree.
- RuoYi login is performed directly against RuoYi-Cloud-Plus, outside
  agent-scope.
- agent-scope derives `userId` from the RuoYi token instead of trusting a
  request-supplied `userId`.
- Sa-Token login-state validation continues to use the shared RuoYi Redis
  store.
- Existing chat and interview API paths remain unchanged.
- `agent-scope` no longer exposes its own `/api/auth/*` endpoints.
- Spring component scanning still finds all controllers, services,
  configuration classes, and infrastructure beans.
- Tests compile after package and import updates.
- No unrelated behavior or configuration changes are introduced.
