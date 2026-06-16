package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InterviewAiJsonClientTest {

    @Test
    void parsesDirectJsonAndRejectsMarkdownFences() {
        ChatTextModel valid = (system, prompt) -> """
                {"question":"Explain volatile","skillTags":["CONCURRENCY"],"evidenceIds":[]}
                """;
        InterviewAiJsonClient client =
                new InterviewAiJsonClient(valid, new ObjectMapper());

        InterviewAiContracts.GeneratedQuestion question = client.call(
                "system",
                "prompt",
                InterviewAiContracts.GeneratedQuestion.class);

        assertEquals("Explain volatile", question.question());

        ChatTextModel fenced = (system, prompt) -> """
                ```json
                {"question":"bad","skillTags":[],"evidenceIds":[]}
                ```
                """;
        assertThrows(
                InterviewAiJsonClient.InvalidOutputException.class,
                () -> new InterviewAiJsonClient(fenced, new ObjectMapper())
                        .call(
                                "system",
                                "prompt",
                                InterviewAiContracts.GeneratedQuestion.class));
    }

    @Test
    void rejectsMissingQuestionAndInvalidFollowUpDecision() {
        ChatTextModel missingQuestion = (system, prompt) ->
                "{\"skillTags\":[],\"evidenceIds\":[]}";
        assertThrows(
                InterviewAiJsonClient.InvalidOutputException.class,
                () -> new InterviewAiJsonClient(
                        missingQuestion,
                        new ObjectMapper())
                        .call(
                                "system",
                                "prompt",
                                InterviewAiContracts.GeneratedQuestion.class));

        ChatTextModel missingFollowUp = (system, prompt) -> """
                {
                  "internalEvaluation":"partial",
                  "abilityTags":["JVM"],
                  "decision":"FOLLOW_UP",
                  "decisionReason":"needs detail"
                }
                """;
        assertThrows(
                InterviewAiJsonClient.InvalidOutputException.class,
                () -> new InterviewAiJsonClient(
                        missingFollowUp,
                        new ObjectMapper())
                        .call(
                                "system",
                                "prompt",
                                InterviewAiContracts.AnswerEvaluation.class));
    }

    @Test
    void parsesNestedScoreReportAndRejectsOutOfRangeScore() {
        ChatTextModel valid = (system, prompt) -> reportJson(78);
        InterviewAiContracts.ReportDraft report =
                new InterviewAiJsonClient(valid, new ObjectMapper()).call(
                        "system",
                        "prompt",
                        InterviewAiContracts.ReportDraft.class);

        assertEquals(78, report.overallScore());
        assertEquals(70, report.scores().concurrency());

        ChatTextModel invalid = (system, prompt) -> reportJson(101);
        assertThrows(
                InterviewAiJsonClient.InvalidOutputException.class,
                () -> new InterviewAiJsonClient(invalid, new ObjectMapper())
                        .call(
                                "system",
                                "prompt",
                                InterviewAiContracts.ReportDraft.class));
    }

    private String reportJson(int overallScore) {
        return """
                {
                  "overallScore": %d,
                  "scores": {
                    "javaFundamentals": 82,
                    "concurrency": 70,
                    "jvm": 75,
                    "spring": 80,
                    "database": 76,
                    "engineering": 84
                  },
                  "strengths": ["clear fundamentals"],
                  "weaknesses": ["concurrency depth"],
                  "improvementSuggestions": ["practice thread-safety design"]
                }
                """.formatted(overallScore);
    }
}
