package com.example.demoscope.biz.interview;

import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewSnapshot;

public final class InterviewTranscriptRenderer {

    private InterviewTranscriptRenderer() {
    }

    public static String transcript(InterviewSnapshot snapshot) {
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
