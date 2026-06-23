package com.example.demoscope.identity.application;

import com.example.demoscope.identity.application.BearerTokenExtractor;
import com.example.demoscope.identity.application.RuoyiSaTokenUserContext;
import com.example.demoscope.identity.domain.SaTokenFacade;
import com.example.demoscope.identity.domain.UnauthenticatedUserException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

class RuoyiSaTokenUserContextTest {

    @Test
    void extractsBearerTokenAndReturnsLoginId() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-123");
        SaTokenFacade facade = token -> "42";
        RuoyiSaTokenUserContext context = new RuoyiSaTokenUserContext(
                new BearerTokenExtractor("Authorization"),
                facade);

        assertEquals("42", context.requireUserId(request));
    }

    @Test
    void rejectsMissingToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        RuoyiSaTokenUserContext context = new RuoyiSaTokenUserContext(
                new BearerTokenExtractor("Authorization"),
                token -> "42");

        assertThrows(UnauthenticatedUserException.class, () -> context.requireUserId(request));
    }

    @Test
    void rejectsInvalidToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        RuoyiSaTokenUserContext context = new RuoyiSaTokenUserContext(
                new BearerTokenExtractor("Authorization"),
                token -> {
                    throw new IllegalStateException("token invalid");
                });

        assertThrows(UnauthenticatedUserException.class, () -> context.requireUserId(request));
    }
}
