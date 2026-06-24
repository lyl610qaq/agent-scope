package com.example.demoscope.biz.auth;

import com.example.demoscope.constant.auth.AuthConstants;
import com.example.demoscope.domain.auth.UnauthenticatedUserException;
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
        if (trimmed.regionMatches(
                true,
                0,
                AuthConstants.BEARER_PREFIX,
                0,
                AuthConstants.BEARER_PREFIX.length())) {
            trimmed = trimmed.substring(AuthConstants.BEARER_PREFIX.length()).trim();
        }
        if (!StringUtils.hasText(trimmed)) {
            throw new UnauthenticatedUserException("missing authentication token");
        }
        return trimmed;
    }
}
