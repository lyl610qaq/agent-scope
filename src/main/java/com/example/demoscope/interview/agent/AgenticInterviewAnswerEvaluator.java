package com.example.demoscope.interview.agent;

import com.example.demoscope.interview.domain.InterviewAnswerEvaluator;
import com.example.demoscope.interview.domain.InterviewQuestion;
import com.example.demoscope.interview.domain.InterviewSnapshot;
import com.example.demoscope.interview.infrastructure.InterviewAiContracts;

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
