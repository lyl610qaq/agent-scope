package com.example.demoscope;

import java.util.List;

public class ModelInterviewAnswerEvaluator
        implements InterviewAnswerEvaluator {

    private static final String SYSTEM_PROMPT = """
            You evaluate one candidate answer in a Java backend interview.
            Return one JSON object only. Do not use markdown.
            Choose FOLLOW_UP only when one focused clarification would materially improve
            the evidence. Otherwise choose NEXT_MAIN_QUESTION.
            Never reveal the internal evaluation to the candidate.
            Schema:
            {"internalEvaluation":"non-blank","abilityTags":["tag"],
            "decision":"FOLLOW_UP|NEXT_MAIN_QUESTION",
            "followUpQuestion":"required only for FOLLOW_UP",
            "decisionReason":"non-blank"}
            """;

    private final InterviewAiJsonClient aiClient;
    private final InterviewEvidenceProvider evidenceProvider;

    public ModelInterviewAnswerEvaluator(
            InterviewAiJsonClient aiClient,
            InterviewEvidenceProvider evidenceProvider) {
        this.aiClient = aiClient;
        this.evidenceProvider = evidenceProvider;
    }

    @Override
    public InterviewAiContracts.AnswerEvaluation evaluate(
            InterviewSnapshot snapshot,
            InterviewQuestion question,
            String candidateAnswer) {
        List<String> evidence = evidenceProvider.retrieve(
                        question.text() + " " + candidateAnswer)
                .stream()
                .map(KnowledgeChunk::content)
                .toList();
        String prompt = """
                Direction: %s
                Difficulty: %s
                Question type: %s
                Main question number: %d
                Follow-up number: %d
                Question: %s
                Candidate answer: %s
                Prior transcript:
                %s
                Evidence:
                %s
                """.formatted(
                snapshot.session().direction(),
                snapshot.session().difficulty(),
                question.type(),
                question.mainQuestionNumber(),
                question.followUpNumber(),
                question.text(),
                candidateAnswer,
                ModelInterviewQuestionGenerator.transcript(snapshot),
                evidence);
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt,
                InterviewAiContracts.AnswerEvaluation.class);
    }
}
