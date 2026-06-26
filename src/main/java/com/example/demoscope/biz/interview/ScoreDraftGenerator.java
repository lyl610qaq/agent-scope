package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;

public interface ScoreDraftGenerator {

    InterviewAiContracts.ReportDraft generate(
            InterviewSnapshot snapshot,
            String reviewFeedback);
}
