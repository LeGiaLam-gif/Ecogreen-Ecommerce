package com.example.backend.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * URL slugs for products and categories. Vietnamese diacritics are removed in Java (Normalizer NFD plus the
 * stand-alone letter d-stroke); SQL regexp_replace is never used because it destroys accented letters.
 * A slug always contains at least one letter, so it can never be confused with a numeric id.
 */
public final class SlugGenerator {

    public static final int MAX_LENGTH = 150;

    private SlugGenerator() {
    }

    /** Lower-case ASCII letters/digits separated by single hyphens; never blank and never digits-only. */
    public static String slugify(String text, String fallbackPrefix) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT).replace('\u0111', 'd');
        String ascii = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            return fallbackPrefix;
        }
        if (!slug.matches(".*[a-z].*")) {
            return fallbackPrefix + "-" + slug;
        }
        return slug;
    }

    /** {@code base}, then {@code base-2}, {@code base-3}, ... until {@code exists} says the candidate is free. */
    public static String unique(String base, Predicate<String> exists) {
        if (!exists.test(base)) {
            return base;
        }
        for (int n = 2; ; n++) {
            String suffix = "-" + n;
            String head = base.length() + suffix.length() > MAX_LENGTH
                    ? base.substring(0, MAX_LENGTH - suffix.length()) : base;
            String candidate = head + suffix;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
    }
}
