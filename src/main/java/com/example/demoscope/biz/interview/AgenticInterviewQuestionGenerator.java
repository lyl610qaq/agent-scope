package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewQuestionGenerator;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;

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
