package com.example.backend.security;

/** Verifies a Facebook user access token with Facebook and returns the identity behind it. */
public interface FacebookIdentityVerifier {

    /** False when FACEBOOK_APP_ID / FACEBOOK_APP_SECRET are not set (Facebook login must fail closed). */
    boolean isConfigured();

    /**
     * @throws com.example.backend.exception.UnauthorizedException if the token is invalid, expired or was issued for another app
     * @throws com.example.backend.exception.FacebookLoginUnavailableException if not configured or the Graph API is unreachable
     */
    FacebookIdentity verify(String accessToken);
}
