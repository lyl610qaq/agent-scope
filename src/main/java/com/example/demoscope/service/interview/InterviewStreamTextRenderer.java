package com.example.demoscope.service.interview;

import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewReport;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InterviewStreamTextRenderer {

    public String render(InterviewSnapshot snapshot) {
        return snapshot.currentQuestion()
                .map(this::renderQuestion)
                .orElseGet(() -> renderWithoutQuestion(snapshot));
    }

    private String renderQuestion(InterviewQuestion question) {
        if (question.type() == InterviewQuestion.Type.FOLLOW_UP) {
            return "追问：" + question.text();
        }
        return "第 " + question.mainQuestionNumber() + " 题：" + question.text();
    }

    private String renderWithoutQuestion(InterviewSnapshot snapshot) {
        if (snapshot.report() != null) {
            return renderReport(snapshot.report());
        }
        InterviewSession.Status status = snapshot.session().status();
        return switch (status) {
            case SCORING_PENDING -> "正在生成评分报告，请稍后。";
            case COMPLETED -> "评分报告已生成。";
            case CANCELLED -> "本次面试已取消。";
            case QUESTION_GENERATION_PENDING -> "正在生成下一道面试题，请稍后。";
            case IN_PROGRESS -> "面试状态已更新。";
        };
    }

    private String renderReport(InterviewReport report) {
        return """
                面试评分完成，总分 %d 分。
                优势：%s
                不足：%s
                改进建议：%s
                """.formatted(
                report.overallScore(),
                join(report.strengths()),
                join(report.weaknesses()),
                join(report.improvementSuggestions()))
                .trim();
    }

    private String join(List<String> values) {
        return String.join("；", values);
    }
}
