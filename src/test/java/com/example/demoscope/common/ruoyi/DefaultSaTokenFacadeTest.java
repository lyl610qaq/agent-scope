package com.example.demoscope.common.ruoyi;

import com.example.demoscope.common.ruoyi.DefaultSaTokenFacade;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;

class DefaultSaTokenFacadeTest {

    @Test
    void resolvesLoginIdThroughSaTokenApi() {
        SaTokenDao previous = SaManager.getSaTokenDao();
        SaTokenDaoDefaultImpl dao = new SaTokenDaoDefaultImpl();
        String key = StpUtil.getStpLogic().splicingKeyTokenValue("token-123");
        try {
            SaManager.setSaTokenDao(dao);
            dao.set(key, "42", 60);

            assertEquals("42", new DefaultSaTokenFacade().getLoginIdByToken("token-123"));
        } finally {
            SaManager.setSaTokenDao(previous);
        }
    }
}
