package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewReportGenerator;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;

public class AgenticInterviewReportGenerator
        implements InterviewReportGenerator, ScoreDraftGenerator {

    private final InterviewAgentOrchestrator orchestrator;

    public AgenticInterviewReportGenerator(
            InterviewAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public InterviewAiContracts.ReportDraft generate(InterviewSnapshot snapshot) {
        return generate(snapshot, null);
    }

    @Override
    public InterviewAiContracts.ReportDraft generate(
            InterviewSnapshot snapshot,
            String reviewFeedback) {
        return orchestrator.generateReport(snapshot, reviewFeedback);
    }
}
