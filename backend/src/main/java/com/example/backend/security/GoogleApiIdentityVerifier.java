package com.example.backend.security;

import com.example.backend.exception.GoogleLoginUnavailableException;
import com.example.backend.exception.UnauthorizedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Real verifier backed by Google's {@link GoogleIdTokenVerifier}: RS256 signature against Google's published
 * keys, issuer in {https://accounts.google.com, accounts.google.com}, audience == GOOGLE_CLIENT_ID, and expiry.
 * {@code email_verified} is returned to the caller and enforced in AuthService. The token is never logged.
 */
@Component
public class GoogleApiIdentityVerifier implements GoogleIdentityVerifier {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final GoogleIdTokenVerifier verifier; // null when GOOGLE_CLIENT_ID is not configured

    public GoogleApiIdentityVerifier(@Value("${GOOGLE_CLIENT_ID:}") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JSON_FACTORY)
                    .setAudience(List.of(clientId.trim()))
                    .setAcceptableTimeSkewSeconds(60)
                    .build();
        }
    }

    @Override
    public boolean isConfigured() {
        return verifier != null;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (verifier == null) {
            throw new GoogleLoginUnavailableException("Đăng nhập bằng Google hiện chưa được cấu hình.");
        }

        GoogleIdToken parsed;
        try {
            parsed = GoogleIdToken.parse(JSON_FACTORY, idToken);
        } catch (IOException | RuntimeException e) {
            throw new UnauthorizedException("Mã xác thực Google không hợp lệ.");
        }

        boolean valid;
        try {
            valid = verifier.verify(parsed);
        } catch (GeneralSecurityException | IOException e) {
            // Could not obtain/use Google's public keys: fail closed, never accept.
            throw new GoogleLoginUnavailableException("Không thể xác thực với Google lúc này. Vui lòng thử lại sau.");
        }
        if (!valid) {
            throw new UnauthorizedException("Mã xác thực Google không hợp lệ hoặc đã hết hạn.");
        }

        GoogleIdToken.Payload payload = parsed.getPayload();
        return new GoogleIdentity(
                payload.getSubject(),
                payload.getEmail(),
                Boolean.TRUE.equals(payload.getEmailVerified()),
                payload.get("name") instanceof String n ? n : null,
                payload.get("picture") instanceof String p ? p : null);
    }
}
