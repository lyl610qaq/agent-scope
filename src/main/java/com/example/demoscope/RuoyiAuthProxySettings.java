package com.example.demoscope;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RuoyiAuthProxySettings(
        URI baseUrl,
        String loginPath,
        String logoutPath,
        String tokenHeaderName,
        Duration connectTimeout,
        Duration readTimeout,
        int maxLoginBodyBytes) {

    private static final Pattern HEADER_NAME =
            Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    public RuoyiAuthProxySettings {
        baseUrl = validateBaseUrl(baseUrl);
        loginPath = validatePath(loginPath, "loginPath");
        logoutPath = validatePath(logoutPath, "logoutPath");
        tokenHeaderName = Objects.requireNonNull(tokenHeaderName, "tokenHeaderName").trim();
        if (!HEADER_NAME.matcher(tokenHeaderName).matches()) {
            throw new IllegalArgumentException(
                    "tokenHeaderName must be a valid HTTP header name");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (maxLoginBodyBytes <= 0) {
            throw new IllegalArgumentException("maxLoginBodyBytes must be positive");
        }
    }

    public URI loginUri() {
        return baseUrl.resolve(loginPath);
    }

    public URI logoutUri() {
        return baseUrl.resolve(logoutPath);
    }

    private static URI validateBaseUrl(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null) {
            throw new IllegalArgumentException(
                    "RuoYi base URL must be an absolute HTTP URL");
        }
        String scheme = value.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(
                    "RuoYi base URL must use HTTP or HTTPS");
        }
        String path = value.getPath();
        if (value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getRawFragment() != null
                || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException(
                    "RuoYi base URL must not contain credentials, a path, query, or fragment");
        }
        return value;
    }

    private static String validatePath(String value, String name) {
        String path = Objects.requireNonNull(value, name).trim();
        URI uri = URI.create(path);
        if (!path.startsWith("/")
                || path.startsWith("//")
                || uri.isAbsolute()
                || uri.getRawAuthority() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !uri.normalize().toString().equals(path)) {
            throw new IllegalArgumentException(
                    name + " must be a normalized absolute path");
        }
        return path;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
