package com.example.demoscope;

import java.util.List;

public class ModelInterviewQuestionGenerator
        implements InterviewQuestionGenerator {

    private static final String SYSTEM_PROMPT = """
            You are a Java backend technical interviewer.
            Return one JSON object only. Do not use markdown.
            Generate exactly one main question suitable for the requested difficulty.
            Do not include an answer, hints, scoring, or private reasoning.
            Schema:
            {"question":"non-blank","skillTags":["tag"],"evidenceIds":["id"]}
            """;

    private final InterviewAiJsonClient aiClient;
    private final InterviewEvidenceProvider evidenceProvider;

    public ModelInterviewQuestionGenerator(
            InterviewAiJsonClient aiClient,
            InterviewEvidenceProvider evidenceProvider) {
        this.aiClient = aiClient;
        this.evidenceProvider = evidenceProvider;
    }

    @Override
    public InterviewAiContracts.GeneratedQuestion generate(
            InterviewSnapshot snapshot,
            int mainQuestionNumber) {
        String retrievalQuery = "Java backend "
                + snapshot.session().difficulty()
                + " interview main question "
                + mainQuestionNumber;
        List<String> evidence = evidenceProvider.retrieve(retrievalQuery)
                .stream()
                .map(KnowledgeChunk::content)
                .toList();
        String prompt = """
                Direction: %s
                Difficulty: %s
                main question number: %d
                Previous transcript:
                %s
                Evidence:
                %s
                """.formatted(
                snapshot.session().direction(),
                snapshot.session().difficulty(),
                mainQuestionNumber,
                transcript(snapshot),
                evidence);
        return aiClient.call(
                SYSTEM_PROMPT,
                prompt,
                InterviewAiContracts.GeneratedQuestion.class);
    }

    static String transcript(InterviewSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        for (InterviewQuestion question : snapshot.questions()) {
            builder.append(question.type())
                    .append(' ')
                    .append(question.mainQuestionNumber())
                    .append('.')
                    .append(question.followUpNumber())
                    .append(": ")
                    .append(question.text())
                    .append('\n');
            snapshot.answerFor(question.id()).ifPresent(answer ->
                    builder.append("Answer: ")
                            .append(answer.answerText())
                            .append('\n')
                            .append("Internal evaluation: ")
                            .append(answer.internalEvaluation())
                            .append('\n'));
        }
        return builder.toString();
    }
}
