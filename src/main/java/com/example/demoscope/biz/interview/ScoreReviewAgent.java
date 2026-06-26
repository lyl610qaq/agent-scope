package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;

public interface ScoreReviewAgent {

    ScoreReviewDecision review(
            InterviewSnapshot snapshot,
            InterviewAiContracts.ReportDraft draft);
}
