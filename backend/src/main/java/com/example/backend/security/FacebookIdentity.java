package com.example.backend.security;

/** Identity read from Facebook's Graph API for a VERIFIED user access token. {@code email} may be null (the user did not grant it). */
public record FacebookIdentity(String id, String name, String email) {
}
