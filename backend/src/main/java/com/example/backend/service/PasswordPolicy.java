package com.example.backend.service;

import com.example.backend.exception.BadRequestException;

import java.nio.charset.StandardCharsets;

/** Server-side password rules shared by registration and password reset (the frontend repeats them for UX only). */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes and Spring Security rejects longer input, so refuse it up front. */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    /** @throws BadRequestException with a user-facing Vietnamese message when the password is not acceptable */
    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Vui lòng nhập mật khẩu.");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BadRequestException("Mật khẩu phải có ít nhất " + MIN_LENGTH + " ký tự.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("Mật khẩu quá dài (tối đa " + MAX_BYTES + " byte).");
        }
    }
}
