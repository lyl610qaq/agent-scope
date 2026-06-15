package com.example.demoscope;

public final class RuoyiAuthProxyException extends RuntimeException {

    public enum Kind {
        UNAVAILABLE,
        TIMEOUT
    }

    private final Kind kind;

    public RuoyiAuthProxyException(Kind kind, Throwable cause) {
        super(kind == Kind.TIMEOUT
                ? "RuoYi authentication request timed out"
                : "RuoYi authentication service is unavailable", cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
