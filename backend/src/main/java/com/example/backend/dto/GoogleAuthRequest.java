package com.example.backend.dto;

/**
 * Body of {@code POST /api/auth/google}: ONLY a Google ID token (JWT).
 * Identity (email, name, sub, picture) is read exclusively from the VERIFIED token on the server;
 * no identity field is accepted from the client. Unknown JSON properties are ignored.
 */
public class GoogleAuthRequest {
    private String idToken;

    public GoogleAuthRequest() {}

    public GoogleAuthRequest(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
