package com.example.demoscope.identity.application;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticatedUserContext {

    String requireUserId(HttpServletRequest request);
}
