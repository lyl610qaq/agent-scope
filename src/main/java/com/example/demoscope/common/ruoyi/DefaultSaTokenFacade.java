package com.example.demoscope.common.ruoyi;

import com.example.demoscope.common.ruoyi.SaTokenFacade;
import cn.dev33.satoken.stp.StpUtil;

public class DefaultSaTokenFacade implements SaTokenFacade {

    @Override
    public Object getLoginIdByToken(String token) {
        return StpUtil.getLoginIdByToken(token);
    }
}
