package com.example.demoscope.domain.interview;

import com.example.demoscope.domain.interview.InterviewAiContracts;

@FunctionalInterface
public interface InterviewReportGenerator {

    InterviewAiContracts.ReportDraft generate(InterviewSnapshot snapshot);
}
