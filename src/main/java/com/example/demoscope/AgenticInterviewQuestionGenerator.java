package com.example.demoscope;

public class AgenticInterviewQuestionGenerator
        implements InterviewQuestionGenerator {

    private final InterviewAgentOrchestrator orchestrator;

    public AgenticInterviewQuestionGenerator(
            InterviewAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public InterviewAiContracts.GeneratedQuestion generate(
            InterviewSnapshot snapshot,
            int mainQuestionNumber) {
        return orchestrator.generateQuestion(snapshot, mainQuestionNumber);
    }
}
