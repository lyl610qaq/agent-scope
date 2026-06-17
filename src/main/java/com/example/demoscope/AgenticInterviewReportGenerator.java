package com.example.demoscope;

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
