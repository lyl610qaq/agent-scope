package com.example.demoscope.interview.agent;

import com.example.demoscope.interview.domain.InterviewQuestionGenerator;
import com.example.demoscope.interview.domain.InterviewSnapshot;
import com.example.demoscope.interview.infrastructure.InterviewAiContracts;

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
