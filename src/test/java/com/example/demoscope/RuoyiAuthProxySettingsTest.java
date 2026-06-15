package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class RuoyiAuthProxySettingsTest {

    @Test
    void buildsValidatedLoginAndLogoutUris() {
        RuoyiAuthProxySettings settings = new RuoyiAuthProxySettings(
                URI.create("http://localhost:8081"),
                "/auth/login",
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                16_384);

        assertEquals(URI.create("http://localhost:8081/auth/login"), settings.loginUri());
        assertEquals(URI.create("http://localhost:8081/auth/logout"), settings.logoutUri());
    }

    @Test
    void rejectsMissingNonHttpOrPathBasedUpstreamUrls() {
        assertThrows(IllegalArgumentException.class, () -> settings(null, "/auth/login"));
        assertThrows(
                IllegalArgumentException.class,
                () -> settings(URI.create("file:///tmp/ruoyi"), "/auth/login"));
        assertThrows(
                IllegalArgumentException.class,
                () -> settings(URI.create("http://localhost:8081/gateway"), "/auth/login"));
    }

    @Test
    void rejectsUnsafePathsHeadersTimeoutsAndBodyLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> settings(URI.create("http://localhost:8081"), "auth/login"));
        assertThrows(
                IllegalArgumentException.class,
                () -> settings(URI.create("http://localhost:8081"), "//evil.example/auth/login"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuoyiAuthProxySettings(
                        URI.create("http://localhost:8081"),
                        "/auth/login",
                        "/auth/logout",
                        "Bad Header",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        16_384));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuoyiAuthProxySettings(
                        URI.create("http://localhost:8081"),
                        "/auth/login",
                        "/auth/logout",
                        "Authorization",
                        Duration.ZERO,
                        Duration.ofSeconds(10),
                        16_384));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuoyiAuthProxySettings(
                        URI.create("http://localhost:8081"),
                        "/auth/login",
                        "/auth/logout",
                        "Authorization",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        0));
    }

    private RuoyiAuthProxySettings settings(URI baseUrl, String loginPath) {
        return new RuoyiAuthProxySettings(
                baseUrl,
                loginPath,
                "/auth/logout",
                "Authorization",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10),
                16_384);
    }
}
