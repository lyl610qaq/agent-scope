package com.example.demoscope;

import cn.dev33.satoken.stp.StpUtil;

public class DefaultSaTokenFacade implements SaTokenFacade {

    @Override
    public Object getLoginIdByToken(String token) {
        return StpUtil.getLoginIdByToken(token);
    }
}
