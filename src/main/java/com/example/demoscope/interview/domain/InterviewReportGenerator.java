package com.example.demoscope.interview.domain;

import com.example.demoscope.interview.infrastructure.InterviewAiContracts;

@FunctionalInterface
public interface InterviewReportGenerator {

    InterviewAiContracts.ReportDraft generate(InterviewSnapshot snapshot);
}
