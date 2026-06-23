package com.example.demoscope.identity.domain;

@FunctionalInterface
public interface SaTokenFacade {

    Object getLoginIdByToken(String token);
}
