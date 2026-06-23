package com.example.demoscope.agent.application;

public interface AgentChatService {

    String chat(String userId, String conversationId, String message);
}
