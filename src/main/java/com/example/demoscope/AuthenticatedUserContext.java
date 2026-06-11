package com.example.demoscope;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticatedUserContext {

    String requireUserId(HttpServletRequest request);
}
