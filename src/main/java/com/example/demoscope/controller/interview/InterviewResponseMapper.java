package com.example.demoscope.controller.interview;

import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import org.springframework.stereotype.Component;

@Component
public class InterviewResponseMapper {

    public InterviewController.InterviewResponse toResponse(InterviewSnapshot snapshot) {
        InterviewSession session = snapshot.session();
        InterviewQuestion current = snapshot.currentQuestion().orElse(null);
        int mainQuestionNumber = current == null
                ? session.mainQuestionCount()
                : current.mainQuestionNumber();
        int followUpNumber = current == null
                ? 0
                : current.followUpNumber();
        InterviewController.QuestionResponse question = current == null
                ? null
                : new InterviewController.QuestionResponse(current.id(), current.text());
        InterviewController.ReportResponse report = snapshot.report() == null
                ? null
                : toReport(snapshot.report());
        return new InterviewController.InterviewResponse(
                session.id(),
                session.status(),
                session.direction(),
                session.difficulty(),
                mainQuestionNumber,
                followUpNumber,
                nextAction(session.status(), current),
                question,
                report);
    }

    private InterviewController.NextAction nextAction(
            InterviewSession.Status status,
            InterviewQuestion current) {
        return switch (status) {
            case SCORING_PENDING -> InterviewController.NextAction.REPORT_PENDING;
            case COMPLETED -> InterviewController.NextAction.REPORT;
            case CANCELLED -> InterviewController.NextAction.CANCELLED;
            case QUESTION_GENERATION_PENDING -> InterviewController.NextAction.MAIN_QUESTION;
            case IN_PROGRESS -> current != null
                    && current.type() == InterviewQuestion.Type.FOLLOW_UP
                            ? InterviewController.NextAction.FOLLOW_UP
                            : InterviewController.NextAction.MAIN_QUESTION;
        };
    }

    private InterviewController.ReportResponse toReport(InterviewReport report) {
        return new InterviewController.ReportResponse(
                report.overallScore(),
                report.javaFundamentalsScore(),
                report.concurrencyScore(),
                report.jvmScore(),
                report.springScore(),
                report.databaseScore(),
                report.engineeringScore(),
                report.strengths(),
                report.weaknesses(),
                report.improvementSuggestions());
    }
}
