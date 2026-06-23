package com.example.demoscope.interview.domain;

import com.example.demoscope.interview.infrastructure.InterviewAiContracts;

@FunctionalInterface
public interface InterviewAnswerEvaluator {

    InterviewAiContracts.AnswerEvaluation evaluate(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer);
}
