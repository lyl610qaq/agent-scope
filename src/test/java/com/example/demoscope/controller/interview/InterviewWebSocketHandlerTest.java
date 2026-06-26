package com.example.demoscope.controller.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.demoscope.domain.interview.InterviewQuestion;
import com.example.demoscope.domain.interview.InterviewSession;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.service.interview.InterviewStreamingFacade;
import com.example.demoscope.service.interview.StreamedInterviewResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class InterviewWebSocketHandlerTest {

    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID QUESTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-06-25T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void streamsCreateActionTextAndSnapshotWithRuoyiTokenUserId() throws Exception {
        InterviewStreamingFacade facade = mock(InterviewStreamingFacade.class);
        whenCreateReturns(facade, new StreamedInterviewResult(
                "第一题：请介绍 JVM 内存模型。",
                activeSnapshot()));
        InterviewWebSocketHandler handler = new InterviewWebSocketHandler(
                facade,
                token -> "user-42",
                new InterviewResponseMapper(),
                objectMapper);
        List<String> sentPayloads = new ArrayList<>();
        WebSocketSession session = sessionSendingTo(sentPayloads);

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "token": "Bearer valid-token",
                  "action": "create",
                  "direction": "JAVA_BACKEND",
                  "difficulty": "MIDDLE"
                }
                """));

        assertEquals("{\"type\":\"start\",\"action\":\"create\"}", sentPayloads.get(0));
        assertEquals("{\"type\":\"delta\",\"content\":\"第一题：请介绍 JVM 内存模型。\"}", sentPayloads.get(1));
        JsonNode snapshot = objectMapper.readTree(sentPayloads.get(2));
        assertEquals("snapshot", snapshot.path("type").asText());
        assertEquals(INTERVIEW_ID.toString(), snapshot.path("data").path("interviewId").asText());
        assertEquals("MAIN_QUESTION", snapshot.path("data").path("nextAction").asText());
        assertEquals("第一题：请介绍 JVM 内存模型。", snapshot.path("data").path("question").path("text").asText());
        assertEquals("{\"type\":\"done\"}", sentPayloads.get(3));
        verify(facade).create(
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE);
    }

    private void whenCreateReturns(
            InterviewStreamingFacade facade,
            StreamedInterviewResult result) {
        doAnswer(invocation -> result)
                .when(facade)
                .create(eq("user-42"), any(), any());
    }

    private InterviewSnapshot activeSnapshot() {
        InterviewSession session = new InterviewSession(
                INTERVIEW_ID,
                "user-42",
                InterviewSession.Direction.JAVA_BACKEND,
                InterviewSession.Difficulty.MIDDLE,
                InterviewSession.Status.IN_PROGRESS,
                1,
                QUESTION_ID,
                0,
                1,
                NOW,
                NOW,
                null);
        InterviewQuestion question = InterviewQuestion.main(
                QUESTION_ID,
                INTERVIEW_ID,
                1,
                "第一题：请介绍 JVM 内存模型。",
                List.of("jvm"),
                List.of(),
                NOW);
        return new InterviewSnapshot(session, List.of(question), List.of(), null);
    }

    private WebSocketSession sessionSendingTo(List<String> sentPayloads) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0, TextMessage.class);
            sentPayloads.add(message.getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        return session;
    }
}
