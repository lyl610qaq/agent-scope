package com.example.demoscope.common.ruoyi;

@FunctionalInterface
public interface SaTokenFacade {

    Object getLoginIdByToken(String token);
}
