package com.example.demoscope.interview.agent;

import com.example.demoscope.interview.domain.InterviewReportGenerator;
import com.example.demoscope.interview.domain.InterviewSnapshot;
import com.example.demoscope.interview.infrastructure.InterviewAiContracts;
import com.example.demoscope.interview.infrastructure.InterviewAiJsonClient;

public class ModelInterviewReportGenerator
        implements InterviewReportGenerator {

    private static final String SYSTEM_PROMPT = """
            You score a completed Java backend interview.
            Return one JSON object only. Do not use markdown.
            Base the report only on the supplied questions, answers, and evaluations.
            All seven scores are integers from 0 to 100.
            Schema:
            {"overallScore":0,
            "scores":{"javaFundamentals":0,"concurrency":0,"jvm":0,
            "spring":0,"database":0,"engineering":0},
            "strengths":["non-blank"],"weaknesses":["non-blank"],
            "improvementSuggestions":["non-blank"]}
            """;

    private final InterviewAiJsonClient aiClient;

    public ModelInterviewReportGenerator(
            InterviewAiJsonClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public InterviewAiContracts.ReportDraft generate(
            InterviewSnapshot snapshot) {
        String prompt = """
                Direction: %s
                Difficulty: %s
                Interview transcript:
                %s
                """.formatted(
                snapshot.session().direction(),
                snapshot.session().difficulty(),
                ModelInterviewQuestionGenerator.transcript(snapshot));
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt,
                InterviewAiContracts.ReportDraft.class);
    }
}
