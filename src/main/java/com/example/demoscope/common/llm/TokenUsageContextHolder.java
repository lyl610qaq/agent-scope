package com.example.demoscope.common.llm;

import java.util.Objects;
import java.util.function.Supplier;

public final class TokenUsageContextHolder {

    private static final ThreadLocal<TokenUsageContext> CONTEXT = new ThreadLocal<>();

    private TokenUsageContextHolder() {
    }

    public static TokenUsageContext current() {
        TokenUsageContext context = CONTEXT.get();
        return context == null ? TokenUsageContext.unknown() : context;
    }

    public static void runWithContext(TokenUsageContext context, Runnable runnable) {
        callWithContext(context, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T callWithContext(TokenUsageContext context, Supplier<T> supplier) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(supplier, "supplier");
        TokenUsageContext previous = CONTEXT.get();
        CONTEXT.set(context);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}
