package com.example.demoscope.biz.auth;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticatedUserContext {

    String requireUserId(HttpServletRequest request);
}
