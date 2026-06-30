package com.example.demoscope.domain.interview;

import com.example.demoscope.domain.interview.InterviewAiContracts;

@FunctionalInterface
public interface InterviewQuestionGenerator {

    InterviewAiContracts.GeneratedQuestion generate(
            InterviewSnapshot snapshot,
            int mainQuestionNumber);
}
