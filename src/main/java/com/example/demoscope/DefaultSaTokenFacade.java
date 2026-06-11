package com.example.demoscope;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DefaultSaTokenFacade implements SaTokenFacade {

    @Override
    public Object getLoginIdByToken(String token) {
        try {
            Class<?> stpUtil = Class.forName("cn.dev33.satoken.stp.StpUtil");
            Method method = stpUtil.getMethod("getLoginIdByToken", String.class);
            return method.invoke(null, token);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Sa-Token is not available on the classpath.", ex);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalStateException("Sa-Token getLoginIdByToken is not accessible.", ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Sa-Token token lookup failed.", cause);
        }
    }
}
