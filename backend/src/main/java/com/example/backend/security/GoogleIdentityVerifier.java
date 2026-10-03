package com.example.backend.security;

/** Verifies a Google ID token and returns the identity it carries. */
public interface GoogleIdentityVerifier {

    /** False when the server-side Google client ID is not configured (Google login must fail closed). */
    boolean isConfigured();

    /**
     * @throws com.example.backend.exception.UnauthorizedException if the token is malformed, forged, expired,
     *         has the wrong issuer/audience or an unsupported algorithm
     * @throws com.example.backend.exception.GoogleLoginUnavailableException if not configured or Google's
     *         signing keys cannot be obtained (fail closed)
     */
    GoogleIdentity verify(String idToken);
}
