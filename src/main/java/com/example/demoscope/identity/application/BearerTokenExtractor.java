package com.example.demoscope.identity.application;

import com.example.demoscope.identity.domain.UnauthenticatedUserException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

public class BearerTokenExtractor {

    private final String tokenHeaderName;

    public BearerTokenExtractor(String tokenHeaderName) {
        this.tokenHeaderName = tokenHeaderName;
    }

    public String extract(HttpServletRequest request) {
        String value = request.getHeader(tokenHeaderName);
        if (!StringUtils.hasText(value)) {
            throw new UnauthenticatedUserException("missing authentication token");
        }

        String trimmed = value.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            trimmed = trimmed.substring("Bearer ".length()).trim();
        }
        if (!StringUtils.hasText(trimmed)) {
            throw new UnauthenticatedUserException("missing authentication token");
        }
        return trimmed;
    }
}
