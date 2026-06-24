package com.example.demoscope.biz.interview;

import com.example.demoscope.biz.rag.InterviewEvidenceProvider;
import com.example.demoscope.biz.memory.InterviewMemoryContextProvider;
import com.example.demoscope.domain.interview.InterviewMemoryWriter;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;
import com.example.demoscope.domain.rag.KnowledgeChunk;
import com.example.demoscope.domain.memory.LongTermMemory;
import com.example.demoscope.domain.memory.MemoryTurn;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterviewAgentOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(InterviewAgentOrchestrator.class);

    private final InterviewRouterAgent routerAgent;
    private final InterviewRagPlannerAgent plannerAgent;
    private final InterviewEvidenceProvider evidenceProvider;
    private final InterviewMemoryContextProvider memoryContextProvider;
    private final InterviewMemoryManagerAgent memoryManagerAgent;
    private final InterviewMemoryWriter memoryWriter;
    private final Map<InterviewAgentName, InterviewTargetAgent> targetAgents;
    private final int maxEvidence;

    public InterviewAgentOrchestrator(
            InterviewRouterAgent routerAgent,
            InterviewRagPlannerAgent plannerAgent,
            InterviewEvidenceProvider evidenceProvider,
            InterviewMemoryContextProvider memoryContextProvider,
            InterviewMemoryManagerAgent memoryManagerAgent,
            InterviewMemoryWriter memoryWriter,
            List<InterviewTargetAgent> targetAgents,
            int maxEvidence) {
        this.routerAgent = routerAgent;
        this.plannerAgent = plannerAgent;
        this.evidenceProvider = evidenceProvider;
        this.memoryContextProvider = memoryContextProvider;
        this.memoryManagerAgent = memoryManagerAgent;
        this.memoryWriter = memoryWriter;
        this.targetAgents = targetAgents.stream()
                .collect(Collectors.toUnmodifiableMap(
                        InterviewTargetAgent::name,
                        Function.identity()));
        this.maxEvidence = Math.max(1, maxEvidence);
    }

    public InterviewAiContracts.GeneratedQuestion generateQuestion(
            InterviewSnapshot snapshot,
            int mainQuestionNumber) {
        InterviewAgentOutput output = run(
                snapshot,
                InterviewAgentTask.generateMainQuestion(
                        snapshot,
                        mainQuestionNumber));
        return output.generatedQuestion();
    }

    public InterviewAiContracts.AnswerEvaluation evaluateAnswer(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer) {
        InterviewAgentOutput output = run(
                snapshot,
                InterviewAgentTask.evaluateAnswer(
                        snapshot,
                        question,
                        candidateAnswer));
        return output.answerEvaluation();
    }

    public InterviewAiContracts.ReportDraft generateReport(
            InterviewSnapshot snapshot) {
        InterviewAgentOutput output = run(
                snapshot,
                InterviewAgentTask.generateReport(snapshot));
        return output.reportDraft();
    }

    private InterviewAgentOutput run(
            InterviewSnapshot snapshot,
            InterviewAgentTask task) {
        AgentPromptContext baseContext = new AgentPromptContext(
                snapshot,
                task,
                task.currentQuestion(),
                task.candidateAnswer(),
                safeShortTerm(snapshot),
                safeLongTerm(snapshot),
                List.of(),
                null);
        RouterDecision decision = route(baseContext, task);
        AgentPromptContext routedContext =
                baseContext.withRouterDecision(decision);
        List<KnowledgeChunk> evidence = retrieveEvidence(
                snapshot,
                routedContext,
                task);
        AgentPromptContext targetContext =
                routedContext.withRagEvidence(evidence);
        InterviewTargetAgent target = targetAgents.get(decision.nextAgent());
        if (target == null) {
            throw new IllegalStateException(
                    "No interview target agent registered: "
                            + decision.nextAgent());
        }
        InterviewAgentOutput output = target.run(targetContext);
        remember(targetContext, output);
        return output;
    }

    private RouterDecision route(
            AgentPromptContext context,
            InterviewAgentTask task) {
        try {
            RouterDecision decision = routerAgent.route(context);
            if (task.allowedAgents().contains(decision.nextAgent())) {
                return decision;
            }
            log.warn("Interview router selected disallowed agent");
        } catch (RuntimeException exception) {
            log.warn("Interview router failed");
        }
        return new RouterDecision(
                task.defaultAgent(),
                "fallback to default agent",
                0.0,
                "default interview flow",
                List.of());
    }

    private List<KnowledgeChunk> retrieveEvidence(
            InterviewSnapshot snapshot,
            AgentPromptContext context,
            InterviewAgentTask task) {
        try {
            RagQueryPlan plan = plannerAgent.plan(context);
            return evidenceProvider.retrieve(plan, maxEvidence);
        } catch (RuntimeException exception) {
            log.warn("Interview RAG planner failed");
            try {
                return evidenceProvider.retrieve(fallbackQuery(snapshot, task));
            } catch (RuntimeException retrievalException) {
                log.warn("Interview fallback evidence retrieval failed");
                return List.of();
            }
        }
    }

    private void remember(
            AgentPromptContext context,
            InterviewAgentOutput output) {
        try {
            MemoryWriteDecision decision =
                    memoryManagerAgent.decide(context, output);
            memoryWriter.write(context.snapshot(), decision);
        } catch (RuntimeException exception) {
            log.warn("Interview memory manager failed");
        }
    }

    private List<MemoryTurn> safeShortTerm(InterviewSnapshot snapshot) {
        try {
            return memoryContextProvider.shortTerm(snapshot);
        } catch (RuntimeException exception) {
            log.warn("Interview short-term memory context failed");
            return List.of();
        }
    }

    private List<LongTermMemory> safeLongTerm(InterviewSnapshot snapshot) {
        try {
            return memoryContextProvider.longTerm(snapshot);
        } catch (RuntimeException exception) {
            log.warn("Interview long-term memory context failed");
            return List.of();
        }
    }

    private String fallbackQuery(
            InterviewSnapshot snapshot,
            InterviewAgentTask task) {
        Objects.requireNonNull(snapshot, "snapshot");
        return switch (task.type()) {
            case GENERATE_MAIN_QUESTION -> "Java backend "
                    + snapshot.session().difficulty()
                    + " interview question "
                    + task.mainQuestionNumber();
            case EVALUATE_ANSWER -> task.currentQuestion().text()
                    + " "
                    + task.candidateAnswer();
            case GENERATE_REPORT -> "Java backend interview scoring transcript";
        };
    }
}
