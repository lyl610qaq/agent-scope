package com.example.demoscope.memory.application;

import com.example.demoscope.memory.domain.LongTermMemoryCandidate;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

public class LongTermMemoryPolicy {

    private static final double MIN_CONFIDENCE = 0.5;
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(api[\\s_-]*key|access[\\s_-]*token|bearer\\s+|password|passwd|secret|sk-[a-z0-9])");

    public boolean isAllowed(LongTermMemoryCandidate candidate) {
        return candidate != null
                && candidate.category() != null
                && StringUtils.hasText(candidate.text())
                && candidate.confidence() >= MIN_CONFIDENCE
                && !SECRET_PATTERN.matcher(candidate.text()).find();
    }
}
