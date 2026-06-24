package com.example.demoscope.biz.interview;

import com.example.demoscope.biz.interview.ModelInterviewAnswerEvaluator;
import com.example.demoscope.biz.interview.ModelInterviewQuestionGenerator;
import com.example.demoscope.biz.interview.ModelInterviewReportGenerator;
import com.example.demoscope.biz.rag.InterviewEvidenceProvider;
import com.example.demoscope.domain.interview.InterviewAnswer;
import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.domain.interview.InterviewAiContracts;
import com.example.demoscope.common.llm.InterviewAiJsonClient;
import com.example.demoscope.domain.rag.KnowledgeChunk;
import com.example.demoscope.common.llm.ChatTextModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelInterviewAiTest {

    @Test
    void questionGenerationIncludesDifficultyNumberAndEvidence() {
        CapturingModel model = new CapturingModel("""
                {"question":"Explain HashMap","skillTags":["JAVA"],"evidenceIds":["jdk-1"]}
                """);
        InterviewEvidenceProvider evidence = new InterviewEvidenceProvider(
                query -> new float[] {0.1f},
                query -> List.of(new KnowledgeChunk("jdk-1", "HashMap evidence")));
        ModelInterviewQuestionGenerator generator =
                new ModelInterviewQuestionGenerator(
                        new InterviewAiJsonClient(model, new ObjectMapper()),
                        evidence);

        InterviewAiContracts.GeneratedQuestion result =
                generator.generate(snapshot(), 2);

        assertEquals("Explain HashMap", result.question());
        assertTrue(model.lastPrompt.contains("MIDDLE"));
        assertTrue(model.lastPrompt.contains("main question number: 2"));
        assertTrue(model.lastPrompt.contains("HashMap evidence"));
    }

    @Test
    void evaluationIncludesCurrentQuestionAndCandidateAnswer() {
        CapturingModel model = new CapturingModel("""
                {
                  "internalEvaluation":"partial",
                  "abilityTags":["JAVA"],
                  "decision":"FOLLOW_UP",
                  "followUpQuestion":"Why powers of two?",
                  "decisionReason":"needs detail"
                }
                """);
        ModelInterviewAnswerEvaluator evaluator =
                new ModelInterviewAnswerEvaluator(
                        new InterviewAiJsonClient(model, new ObjectMapper()),
                        emptyEvidence());
        InterviewQuestion question = snapshot().currentQuestion().orElseThrow();

        InterviewAiContracts.AnswerEvaluation result = evaluator.evaluate(
                snapshot(),
                question,
                "It uses an array and linked lists.");

        assertEquals(
                InterviewAnswer.Decision.FOLLOW_UP,
                result.decision());
        assertTrue(model.lastPrompt.contains("Explain HashMap"));
        assertTrue(model.lastPrompt.contains("array and linked lists"));
    }

    @Test
    void reportIncludesPersistedMainAndFollowUpExchanges() {
        CapturingModel model = new CapturingModel("""
                {
                  "overallScore":78,
                  "scores":{
                    "javaFundamentals":82,
                    "concurrency":70,
                    "jvm":75,
                    "spring":80,
                    "database":76,
                    "engineering":84
                  },
                  "strengths":["clear fundamentals"],
                  "weaknesses":["concurrency depth"],
                  "improvementSuggestions":["practice thread safety"]
                }
                """);
        ModelInterviewReportGenerator generator =
                new ModelInterviewReportGenerator(
                        new InterviewAiJsonClient(model, new ObjectMapper()));

        InterviewAiContracts.ReportDraft result =
                generator.generate(answeredSnapshot());

        assertEquals(78, result.overallScore());
        assertTrue(model.lastPrompt.contains("Explain HashMap"));
        assertTrue(model.lastPrompt.contains("candidate answer"));
        assertTrue(model.lastPrompt.contains("internal evaluation"));
    }

    @Test
    void evidenceFailureStillCallsModelWithoutCandidateDataInLogs() {
        CapturingModel model = new CapturingModel("""
                {"question":"Explain JVM memory","skillTags":["JVM"],"evidenceIds":[]}
                """);
        InterviewEvidenceProvider evidence = new InterviewEvidenceProvider(
                query -> {
                    throw new IllegalStateException("embedding unavailable");
                },
                query -> List.of());
        ModelInterviewQuestionGenerator generator =
                new ModelInterviewQuestionGenerator(
                        new InterviewAiJsonClient(model, new ObjectMapper()),
                        evidence);

        InterviewAiContracts.GeneratedQuestion result =
                generator.generate(snapshot(), 2);

        assertEquals("Explain JVM memory", result.question());
        assertTrue(model.lastPrompt.contains("Evidence:\n[]"));
    }

    private InterviewEvidenceProvider emptyEvidence() {
        return new InterviewEvidenceProvider(
                query -> new float[] {0.1f},
                query -> List.of());
    }

    private InterviewSnapshot snapshot() {
        UUID interviewId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001");
        UUID questionId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002");
        InterviewQuestion question = InterviewQuestion.main(
                questionId,
                interviewId,
                1,
                "Explain HashMap",
                List.of("JAVA"),
                List.of(),
                Instant.EPOCH);
        InterviewSession session = new InterviewSession(
                interviewId,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.IN_PROGRESS,
                1,
                questionId,
                0,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
        return new InterviewSnapshot(
                session,
                List.of(question),
                List.of(),
                null);
    }

    private InterviewSnapshot answeredSnapshot() {
        InterviewSnapshot base = snapshot();
        InterviewQuestion waiting = base.questions().get(0);
        InterviewQuestion answered = new InterviewQuestion(
                waiting.id(),
                waiting.interviewId(),
                waiting.type(),
                waiting.mainQuestionNumber(),
                waiting.followUpNumber(),
                waiting.parentQuestionId(),
                waiting.text(),
                waiting.skillTags(),
                waiting.evidenceIds(),
                InterviewQuestion.Status.ANSWERED,
                waiting.createdAt(),
                Instant.EPOCH);
        InterviewAnswer answer = new InterviewAnswer(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                base.session().id(),
                answered.id(),
                "candidate answer",
                "internal evaluation",
                List.of("JAVA"),
                InterviewAnswer.Decision.NEXT_MAIN_QUESTION,
                "complete",
                Instant.EPOCH);
        return new InterviewSnapshot(
                base.session(),
                List.of(answered),
                List.of(answer),
                null);
    }

    private static final class CapturingModel implements ChatTextModel {

        private final List<String> responses = new ArrayList<>();
        private String lastPrompt;

        private CapturingModel(String response) {
            responses.add(response);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            lastPrompt = userPrompt;
            return responses.remove(0);
        }
    }
}
