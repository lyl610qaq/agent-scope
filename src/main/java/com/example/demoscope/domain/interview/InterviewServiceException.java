package com.example.demoscope.domain.interview;

import java.util.Objects;

public final class InterviewServiceException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
        AI_UNAVAILABLE
    }

    private final Kind kind;
    private final InterviewSnapshot snapshot;

    public InterviewServiceException(
            Kind kind,
            String message,
            InterviewSnapshot snapshot) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.snapshot = snapshot;
    }

    public InterviewServiceException(
            Kind kind,
            String message,
            InterviewSnapshot snapshot,
            Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.snapshot = snapshot;
    }

    public Kind kind() {
        return kind;
    }

    public InterviewSnapshot snapshot() {
        return snapshot;
    }
}
