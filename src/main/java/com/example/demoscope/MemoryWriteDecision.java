package com.example.demoscope;

import java.util.List;

public record MemoryWriteDecision(
        List<String> shortTermWrites,
        List<String> longTermWrites,
        String reason) {

    public MemoryWriteDecision {
        shortTermWrites = RouterDecision.copyTextList(
                shortTermWrites,
                "shortTermWrites");
        longTermWrites = RouterDecision.copyTextList(
                longTermWrites,
                "longTermWrites");
        reason = RouterDecision.requireText(reason, "reason");
    }
}
