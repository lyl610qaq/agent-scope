package com.example.demoscope;

@FunctionalInterface
public interface InterviewReportGenerator {

    InterviewAiContracts.ReportDraft generate(InterviewSnapshot snapshot);
}
