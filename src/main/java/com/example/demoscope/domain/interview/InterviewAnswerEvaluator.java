package com.example.demoscope.domain.interview;

import com.example.demoscope.domain.interview.InterviewAiContracts;

@FunctionalInterface
public interface InterviewAnswerEvaluator {

    InterviewAiContracts.AnswerEvaluation evaluate(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer);
}
