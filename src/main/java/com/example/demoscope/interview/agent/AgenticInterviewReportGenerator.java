package com.example.demoscope.interview.agent;

import com.example.demoscope.interview.domain.InterviewReportGenerator;
import com.example.demoscope.interview.domain.InterviewSnapshot;
import com.example.demoscope.interview.infrastructure.InterviewAiContracts;

public class AgenticInterviewReportGenerator
        implements InterviewReportGenerator {

    private final InterviewAgentOrchestrator orchestrator;

    public AgenticInterviewReportGenerator(
            InterviewAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public InterviewAiContracts.ReportDraft generate(InterviewSnapshot snapshot) {
        return orchestrator.generateReport(snapshot);
    }
}
