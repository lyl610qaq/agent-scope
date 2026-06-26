package com.example.demoscope.controller.interview;

import com.example.demoscope.common.ruoyi.SaTokenFacade;
import com.example.demoscope.controller.stream.StreamMessage;
import com.example.demoscope.domain.auth.UnauthenticatedUserException;
import com.example.demoscope.domain.interview.InterviewServiceException;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.service.interview.InterviewStreamingFacade;
import com.example.demoscope.service.interview.StreamedInterviewResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@ConditionalOnProperty(
        name = "agentscope.interview.enabled",
        havingValue = "true")
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private static final int TEXT_CHUNK_SIZE = 32;
    private static final String BEARER_PREFIX = "Bearer ";

    private final InterviewStreamingFacade interviewStreamingFacade;
    private final SaTokenFacade saTokenFacade;
    private final InterviewResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    public InterviewWebSocketHandler(
            InterviewStreamingFacade interviewStreamingFacade,
            SaTokenFacade saTokenFacade,
            InterviewResponseMapper responseMapper,
            ObjectMapper objectMapper) {
        this.interviewStreamingFacade = interviewStreamingFacade;
        this.saTokenFacade = saTokenFacade;
        this.responseMapper = responseMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            InterviewStreamRequest request = objectMapper.readValue(
                    message.getPayload(),
                    InterviewStreamRequest.class);
            String action = action(request);
            String userId = requireUserId(request.token());
            StreamedInterviewResult result = invoke(action, userId, request);

            send(session, StreamMessage.start(action));
            sendText(session, result.text());
            send(session, StreamMessage.snapshot(responseMapper.toResponse(result.snapshot())));
            send(session, StreamMessage.done());
        } catch (UnauthenticatedUserException | IllegalArgumentException | InterviewServiceException ex) {
            send(session, StreamMessage.error(ex.getMessage()));
        } catch (JsonProcessingException ex) {
            send(session, StreamMessage.error("invalid websocket payload"));
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        } catch (RuntimeException ex) {
            send(session, StreamMessage.error(ex.getMessage() == null ? "stream failed" : ex.getMessage()));
        }
    }

    private String action(InterviewStreamRequest request) {
        if (request == null || !StringUtils.hasText(request.action())) {
            throw new IllegalArgumentException("action must not be blank");
        }
        return request.action().trim().toLowerCase(Locale.ROOT);
    }

    private StreamedInterviewResult invoke(
            String action,
            String userId,
            InterviewStreamRequest request) {
        return switch (action) {
            case "create" -> {
                if (request.direction() == null || request.difficulty() == null) {
                    throw new IllegalArgumentException("unsupported interview configuration");
                }
                yield interviewStreamingFacade.create(userId, request.direction(), request.difficulty());
            }
            case "current" -> interviewStreamingFacade.current(userId);
            case "get" -> interviewStreamingFacade.get(userId, requireInterviewId(request.interviewId()));
            case "answer" -> interviewStreamingFacade.answer(
                    userId,
                    requireInterviewId(request.interviewId()),
                    requireQuestionId(request.questionId()),
                    requireAnswer(request.answer()));
            case "finish" -> interviewStreamingFacade.finish(userId, requireInterviewId(request.interviewId()));
            default -> throw new IllegalArgumentException("unsupported interview action");
        };
    }

    private UUID requireInterviewId(UUID interviewId) {
        if (interviewId == null) {
            throw new IllegalArgumentException("interviewId must not be blank");
        }
        return interviewId;
    }

    private UUID requireQuestionId(UUID questionId) {
        if (questionId == null) {
            throw new IllegalArgumentException("questionId must not be blank");
        }
        return questionId;
    }

    private String requireAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        return answer;
    }

    private String requireUserId(String token) {
        String normalizedToken = normalizeToken(token);
        try {
            Object loginId = saTokenFacade.getLoginIdByToken(normalizedToken);
            String userId = loginId == null ? "" : loginId.toString();
            if (!StringUtils.hasText(userId)) {
                throw new UnauthenticatedUserException("invalid authentication token");
            }
            return userId;
        } catch (UnauthenticatedUserException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UnauthenticatedUserException("invalid authentication token", ex);
        }
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new UnauthenticatedUserException("invalid authentication token");
        }
        String value = token.trim();
        if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            value = value.substring(BEARER_PREFIX.length()).trim();
        }
        if (!StringUtils.hasText(value)) {
            throw new UnauthenticatedUserException("invalid authentication token");
        }
        return value;
    }

    private void sendText(WebSocketSession session, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (int start = 0; start < text.length(); start += TEXT_CHUNK_SIZE) {
            int end = Math.min(start + TEXT_CHUNK_SIZE, text.length());
            sendUnchecked(session, StreamMessage.delta(text.substring(start, end)));
        }
    }

    private void sendUnchecked(WebSocketSession session, StreamMessage message) {
        try {
            send(session, message);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void send(WebSocketSession session, StreamMessage message) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    private record InterviewStreamRequest(
            String token,
            String action,
            UUID interviewId,
            UUID questionId,
            String answer,
            InterviewSession.Direction direction,
            InterviewSession.Difficulty difficulty) {
    }
}
