package com.example.demoscope.controller.interview;

import com.example.demoscope.biz.auth.AuthenticatedUserContext;
import com.example.demoscope.domain.auth.UnauthenticatedUserException;
import com.example.demoscope.service.interview.InterviewService;
import com.example.demoscope.domain.interview.InterviewServiceException;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/interviews")
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true")
public class InterviewController {

    private final InterviewService service;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final InterviewResponseMapper responseMapper = new InterviewResponseMapper();

    public InterviewController(
            InterviewService service,
            AuthenticatedUserContext authenticatedUserContext) {
        this.service = service;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    @PostMapping
    public ResponseEntity<InterviewResponse> create(
            HttpServletRequest servletRequest,
            @RequestBody CreateInterviewRequest request) {
        if (request == null
                || request.direction() == null
                || request.difficulty() == null) {
            throw badRequest();
        }
        String userId = requireUserId(servletRequest);
        return invoke(() -> service.createOrResume(
                userId,
                request.direction(),
                request.difficulty()));
    }

    @GetMapping("/current")
    public ResponseEntity<InterviewResponse> current(
            HttpServletRequest servletRequest) {
        String userId = requireUserId(servletRequest);
        return invoke(() -> service.current(userId));
    }

    @GetMapping("/{interviewId}")
    public ResponseEntity<InterviewResponse> get(
            HttpServletRequest servletRequest,
            @PathVariable UUID interviewId) {
        String userId = requireUserId(servletRequest);
        return invoke(() -> service.get(userId, interviewId));
    }

    @PostMapping("/{interviewId}/answers")
    public ResponseEntity<InterviewResponse> answer(
            HttpServletRequest servletRequest,
            @PathVariable UUID interviewId,
            @RequestBody SubmitAnswerRequest request) {
        if (request == null
                || request.questionId() == null
                || !StringUtils.hasText(request.answer())) {
            throw badRequest();
        }
        String userId = requireUserId(servletRequest);
        return invoke(() -> service.answer(
                userId,
                interviewId,
                request.questionId(),
                request.answer()));
    }

    @PostMapping("/{interviewId}/finish")
    public ResponseEntity<InterviewResponse> finish(
            HttpServletRequest servletRequest,
            @PathVariable UUID interviewId) {
        String userId = requireUserId(servletRequest);
        return invoke(() -> service.finish(userId, interviewId));
    }

    private String requireUserId(HttpServletRequest request) {
        try {
            return authenticatedUserContext.requireUserId(request);
        } catch (UnauthenticatedUserException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    exception.getMessage(),
                    exception);
        }
    }

    private ResponseEntity<InterviewResponse> invoke(
            Supplier<InterviewSnapshot> operation) {
        try {
            return response(operation.get());
        } catch (InterviewServiceException exception) {
            if (exception.kind()
                    == InterviewServiceException.Kind.AI_UNAVAILABLE
                    && exception.snapshot() != null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(responseMapper.toResponse(exception.snapshot()));
            }
            throw new ResponseStatusException(
                    status(exception.kind()),
                    exception.getMessage(),
                    exception);
        }
    }

    private ResponseEntity<InterviewResponse> response(
            InterviewSnapshot snapshot) {
        HttpStatus status = snapshot.session().status()
                == InterviewSession.Status.SCORING_PENDING
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(responseMapper.toResponse(snapshot));
    }

    private HttpStatus status(InterviewServiceException.Kind kind) {
        return switch (kind) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case AI_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private ResponseStatusException badRequest() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "unsupported interview configuration");
    }

    public record CreateInterviewRequest(
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty) {
    }

    public record SubmitAnswerRequest(UUID questionId, String answer) {
    }

    public record InterviewResponse(
            UUID interviewId,
            InterviewSession.Status status,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty,
            int mainQuestionNumber,
            int followUpNumber,
            NextAction nextAction,
            QuestionResponse question,
            ReportResponse report) {
    }

    public enum NextAction {
        FOLLOW_UP,
        MAIN_QUESTION,
        REPORT_PENDING,
        REPORT,
        CANCELLED
    }

    public record QuestionResponse(UUID questionId, String text) {
    }

    public record ReportResponse(
            int overallScore,
            int javaFundamentalsScore,
            int concurrencyScore,
            int jvmScore,
            int springScore,
            int databaseScore,
            int engineeringScore,
            List<String> strengths,
            List<String> weaknesses,
            List<String> improvementSuggestions) {
    }
}
