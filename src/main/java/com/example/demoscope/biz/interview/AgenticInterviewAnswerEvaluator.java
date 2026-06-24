package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewAnswerEvaluator;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;

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
