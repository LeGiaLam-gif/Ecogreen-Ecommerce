package com.example.backend.dto;

public class GoogleAuthRequest {
    private String credential; // Google ID Token (JWT)
    private String email;      // Google account email
    private String name;       // User's Google display name
    private String googleId;   // Google sub / account identifier
    private String avatar;     // Profile picture URL

    public GoogleAuthRequest() {}

    public GoogleAuthRequest(String credential, String email, String name, String googleId, String avatar) {
        this.credential = credential;
        this.email = email;
        this.name = name;
        this.googleId = googleId;
        this.avatar = avatar;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
