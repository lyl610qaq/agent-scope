package com.example.demoscope;

@FunctionalInterface
public interface InterviewQuestionGenerator {

    InterviewAiContracts.GeneratedQuestion generate(
            InterviewSnapshot snapshot,
            int mainQuestionNumber);
}
