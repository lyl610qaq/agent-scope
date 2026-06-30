package com.example.demoscope.service.interview;

import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true")
public class InterviewStreamingFacade {

    private final InterviewService interviewService;
    private final InterviewStreamTextRenderer textRenderer;

    public InterviewStreamingFacade(
            InterviewService interviewService,
            InterviewStreamTextRenderer textRenderer) {
        this.interviewService = interviewService;
        this.textRenderer = textRenderer;
    }

    public StreamedInterviewResult create(
            String userId,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty) {
        return result(interviewService.createOrResume(userId, direction, difficulty));
    }

    public StreamedInterviewResult current(String userId) {
        return result(interviewService.current(userId));
    }

    public StreamedInterviewResult get(String userId, UUID interviewId) {
        return result(interviewService.get(userId, interviewId));
    }

    public StreamedInterviewResult answer(
            String userId,
            UUID interviewId,
            UUID questionId,
            String answer) {
        return result(interviewService.answer(userId, interviewId, questionId, answer));
    }

    public StreamedInterviewResult finish(String userId, UUID interviewId) {
        return result(interviewService.finish(userId, interviewId));
    }

    private StreamedInterviewResult result(InterviewSnapshot snapshot) {
        return new StreamedInterviewResult(textRenderer.render(snapshot), snapshot);
    }
}
