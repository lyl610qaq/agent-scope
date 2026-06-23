package com.example.demoscope.interview.domain;

import com.example.demoscope.interview.infrastructure.InterviewAiContracts;

@FunctionalInterface
public interface InterviewQuestionGenerator {

    InterviewAiContracts.GeneratedQuestion generate(
            InterviewSnapshot snapshot,
            int mainQuestionNumber);
}
