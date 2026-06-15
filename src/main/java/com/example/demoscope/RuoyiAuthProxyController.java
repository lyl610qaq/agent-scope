package com.example.demoscope;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class RuoyiAuthProxyController {

    private final RuoyiAuthProxyClient proxyClient;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final RuoyiAuthProxySettings settings;

    public RuoyiAuthProxyController(
            RuoyiAuthProxyClient proxyClient,
            AuthenticatedUserContext authenticatedUserContext,
            RuoyiAuthProxySettings settings) {
        this.proxyClient = proxyClient;
        this.authenticatedUserContext = authenticatedUserContext;
        this.settings = settings;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> login(HttpServletRequest request) {
        return proxy(() -> proxyClient.login(readLoginBody(request)));
    }

    @GetMapping("/me")
    public CurrentSessionResponse me(HttpServletRequest request) {
        return new CurrentSessionResponse(true, requireUserId(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<byte[]> logout(HttpServletRequest request) {
        requireUserId(request);
        String tokenHeaderValue = request.getHeader(settings.tokenHeaderName());
        return proxy(() -> proxyClient.logout(tokenHeaderValue));
    }

    private byte[] readLoginBody(HttpServletRequest request) {
        int limit = settings.maxLoginBodyBytes();
        if (request.getContentLengthLong() > limit) {
            throw new ResponseStatusException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "login request body is too large");
        }
        try {
            byte[] body = request.getInputStream().readNBytes(limit + 1);
            if (body.length == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "login request body must not be empty");
            }
            if (body.length > limit) {
                throw new ResponseStatusException(
                        HttpStatus.CONTENT_TOO_LARGE,
                        "login request body is too large");
            }
            return body;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "login request body could not be read",
                    exception);
        }
    }

    private String requireUserId(HttpServletRequest request) {
        try {
            return authenticatedUserContext.requireUserId(request);
        } catch (UnauthenticatedUserException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    exception.getMessage(),
                    exception);
        }
    }

    private ResponseEntity<byte[]> proxy(
            Supplier<RuoyiAuthProxyResponse> operation) {
        try {
            return operation.get().toResponseEntity();
        } catch (RuoyiAuthProxyException exception) {
            HttpStatus status = exception.kind() == RuoyiAuthProxyException.Kind.TIMEOUT
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, exception.getMessage());
        }
    }

    public record CurrentSessionResponse(boolean authenticated, String userId) {
    }
}
