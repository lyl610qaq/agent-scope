package com.example.demoscope.controller.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class StreamMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesDeltaAndDoneMessagesWithoutNullFields() throws Exception {
        assertEquals(
                "{\"type\":\"delta\",\"content\":\"hello\"}",
                objectMapper.writeValueAsString(StreamMessage.delta("hello")));
        assertEquals(
                "{\"type\":\"done\"}",
                objectMapper.writeValueAsString(StreamMessage.done()));
    }

    @Test
    void serializesStartSnapshotAndErrorMessages() throws Exception {
        assertEquals(
                "{\"type\":\"start\",\"action\":\"chat\"}",
                objectMapper.writeValueAsString(StreamMessage.start("chat")));
        assertEquals(
                "{\"type\":\"snapshot\",\"data\":{\"answer\":\"ok\"}}",
                objectMapper.writeValueAsString(
                        StreamMessage.snapshot(new Snapshot("ok"))));
        assertEquals(
                "{\"type\":\"error\",\"message\":\"failed\"}",
                objectMapper.writeValueAsString(StreamMessage.error("failed")));
    }

    private record Snapshot(String answer) {
    }
}
