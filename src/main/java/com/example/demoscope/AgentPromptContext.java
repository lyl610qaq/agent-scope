package com.example.demoscope;

import java.util.List;
import java.util.Objects;

public record AgentPromptContext(
        InterviewSnapshot snapshot,
        InterviewAgentTask task,
        InterviewQuestion currentQuestion,
        String candidateAnswer,
        List<MemoryTurn> shortTermMemory,
        List<LongTermMemory> longTermMemory,
        List<KnowledgeChunk> ragEvidence,
        RouterDecision routerDecision) {

    public AgentPromptContext {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(task, "task");
        shortTermMemory = List.copyOf(
                Objects.requireNonNull(shortTermMemory, "shortTermMemory"));
        longTermMemory = List.copyOf(
                Objects.requireNonNull(longTermMemory, "longTermMemory"));
        ragEvidence = List.copyOf(
                Objects.requireNonNull(ragEvidence, "ragEvidence"));
    }

    public AgentPromptContext withRouterDecision(RouterDecision decision) {
        return new AgentPromptContext(
                snapshot,
                task,
                currentQuestion,
                candidateAnswer,
                shortTermMemory,
                longTermMemory,
                ragEvidence,
                decision);
    }

    public AgentPromptContext withRagEvidence(List<KnowledgeChunk> evidence) {
        return new AgentPromptContext(
                snapshot,
                task,
                currentQuestion,
                candidateAnswer,
                shortTermMemory,
                longTermMemory,
                evidence,
                routerDecision);
    }
}
