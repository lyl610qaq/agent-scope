package com.example.demoscope.biz.auth;

import com.example.demoscope.common.ruoyi.SaTokenFacade;
import com.example.demoscope.domain.auth.UnauthenticatedUserException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

public class RuoyiSaTokenUserContext implements AuthenticatedUserContext {

    private final BearerTokenExtractor tokenExtractor;
    private final SaTokenFacade saTokenFacade;

    public RuoyiSaTokenUserContext(
            BearerTokenExtractor tokenExtractor,
            SaTokenFacade saTokenFacade) {
        this.tokenExtractor = tokenExtractor;
        this.saTokenFacade = saTokenFacade;
    }

    @Override
    public String requireUserId(HttpServletRequest request) {
        String token = tokenExtractor.extract(request);
        try {
            Object loginId = saTokenFacade.getLoginIdByToken(token);
            String userId = loginId == null ? "" : loginId.toString();
            if (!StringUtils.hasText(userId)) {
                throw new UnauthenticatedUserException("invalid authentication token");
            }
            return userId;
        } catch (UnauthenticatedUserException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UnauthenticatedUserException("invalid authentication token", ex);
        }
    }
}
