package com.example.demoscope;

public class AgenticInterviewAnswerEvaluator
        implements InterviewAnswerEvaluator {

    private final InterviewAgentOrchestrator orchestrator;

    public AgenticInterviewAnswerEvaluator(
            InterviewAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public InterviewAiContracts.AnswerEvaluation evaluate(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer) {
        return orchestrator.evaluateAnswer(snapshot, question, candidateAnswer);
    }
}
