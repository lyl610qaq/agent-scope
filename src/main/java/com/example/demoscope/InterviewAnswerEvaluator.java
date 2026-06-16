package com.example.demoscope;

@FunctionalInterface
public interface InterviewAnswerEvaluator {

    InterviewAiContracts.AnswerEvaluation evaluate(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer);
}
