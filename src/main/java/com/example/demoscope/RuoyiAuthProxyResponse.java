package com.example.demoscope;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public record RuoyiAuthProxyResponse(
        int statusCode,
        MediaType contentType,
        byte[] body) {

    public RuoyiAuthProxyResponse {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public ResponseEntity<byte[]> toResponseEntity() {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(statusCode);
        if (contentType != null) {
            builder.contentType(contentType);
        }
        return builder.body(body());
    }
}
