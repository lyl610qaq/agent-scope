package com.example.demoscope;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RuoyiAuthProxyConfig {

    @Bean
    RuoyiAuthProxySettings ruoyiAuthProxySettings(
            @Value("${agentscope.auth.ruoyi.base-url:}") String baseUrl,
            @Value("${agentscope.auth.ruoyi.login-path:/auth/login}") String loginPath,
            @Value("${agentscope.auth.ruoyi.logout-path:/auth/logout}") String logoutPath,
            @Value("${agentscope.auth.ruoyi.token-name:Authorization}") String tokenHeaderName,
            @Value("${agentscope.auth.ruoyi.connect-timeout:3s}") Duration connectTimeout,
            @Value("${agentscope.auth.ruoyi.read-timeout:10s}") Duration readTimeout,
            @Value("${agentscope.auth.ruoyi.max-login-body-bytes:16384}")
                    int maxLoginBodyBytes) {
        return settings(
                baseUrl,
                loginPath,
                logoutPath,
                tokenHeaderName,
                connectTimeout,
                readTimeout,
                maxLoginBodyBytes);
    }

    @Bean("ruoyiAuthRestClient")
    RestClient ruoyiAuthRestClient(RuoyiAuthProxySettings settings) {
        return restClient(settings);
    }

    @Bean
    RuoyiAuthProxyClient ruoyiAuthProxyClient(
            @Qualifier("ruoyiAuthRestClient") RestClient restClient,
            RuoyiAuthProxySettings settings) {
        return new RuoyiAuthProxyClient(restClient, settings);
    }

    static RuoyiAuthProxySettings settings(
            String baseUrl,
            String loginPath,
            String logoutPath,
            String tokenHeaderName,
            Duration connectTimeout,
            Duration readTimeout,
            int maxLoginBodyBytes) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "AGENTSCOPE_RUOYI_BASE_URL must be configured");
        }
        return new RuoyiAuthProxySettings(
                URI.create(baseUrl),
                loginPath,
                logoutPath,
                tokenHeaderName,
                connectTimeout,
                readTimeout,
                maxLoginBodyBytes);
    }

    static RestClient restClient(RuoyiAuthProxySettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
