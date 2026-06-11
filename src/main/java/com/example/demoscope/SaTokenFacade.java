package com.example.demoscope;

@FunctionalInterface
public interface SaTokenFacade {

    Object getLoginIdByToken(String token);
}
