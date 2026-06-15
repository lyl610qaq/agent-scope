package com.example.demoscope;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

public class RuoyiAuthProxyClient {

    private final RestClient restClient;
    private final RuoyiAuthProxySettings settings;

    public RuoyiAuthProxyClient(
            RestClient restClient,
            RuoyiAuthProxySettings settings) {
        this.restClient = restClient;
        this.settings = settings;
    }

    public RuoyiAuthProxyResponse login(byte[] jsonBody) {
        return exchange(restClient.post()
                .uri(settings.loginUri())
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(jsonBody.length)
                .body(jsonBody));
    }

    public RuoyiAuthProxyResponse logout(String tokenHeaderValue) {
        return exchange(restClient.post()
                .uri(settings.logoutUri())
                .header(settings.tokenHeaderName(), tokenHeaderValue));
    }

    private RuoyiAuthProxyResponse exchange(
            RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange((httpRequest, response) ->
                    new RuoyiAuthProxyResponse(
                            response.getStatusCode().value(),
                            response.getHeaders().getContentType(),
                            response.getBody().readAllBytes()));
        } catch (ResourceAccessException exception) {
            RuoyiAuthProxyException.Kind kind = isTimeout(exception)
                    ? RuoyiAuthProxyException.Kind.TIMEOUT
                    : RuoyiAuthProxyException.Kind.UNAVAILABLE;
            throw new RuoyiAuthProxyException(kind, exception);
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
