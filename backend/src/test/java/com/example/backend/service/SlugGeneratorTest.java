package com.example.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlugGeneratorTest {

    @Test
    void slug_vietnameseName_isTransliterated() {
        assertEquals("ao-thun-dep", SlugGenerator.slugify("Áo Thun Đẹp", "product"));
        assertEquals("nha-cua-noi-that-xanh", SlugGenerator.slugify("Nhà Cửa & Nội Thất Xanh", "category"));
        assertEquals("keo-tre-em-300ml", SlugGenerator.slugify("  Kẹo trẻ em -- 300ml!!  ", "product"));
        assertEquals("duong-dua-dong-d", SlugGenerator.slugify("Đường Dừa Đồng Đ", "product"));
    }

    @Test
    void slug_numericOnly_isRejectedOrPrefixed() {
        assertEquals("product-12345", SlugGenerator.slugify("12345", "product"));
        assertEquals("product-2024-10", SlugGenerator.slugify("2024 / 10", "product"));
    }

    @Test
    void slug_blankOrSymbolsOnly_fallsBackToPrefix() {
        assertEquals("product", SlugGenerator.slugify("", "product"));
        assertEquals("product", SlugGenerator.slugify(null, "product"));
        assertEquals("category", SlugGenerator.slugify("!!! ???", "category"));
    }

    @Test
    void slug_alwaysContainsALetter_andOnlySafeCharacters() {
        for (String in : new String[]{"123", "0", "٣٤٥", "Ünïcödé ☃ 77", "a_b%c d", "../../etc/passwd"}) {
            String s = SlugGenerator.slugify(in, "product");
            assertTrue(s.matches("[a-z0-9]+(-[a-z0-9]+)*"), s);
            assertTrue(s.matches(".*[a-z].*"), s);
        }
    }

    @Test
    void slug_isTruncatedToMaxLength() {
        String s = SlugGenerator.slugify("a".repeat(400), "product");
        assertEquals(SlugGenerator.MAX_LENGTH, s.length());
    }

    @Test
    void unique_appendsNumericSuffixUntilFree() {
        Set<String> taken = Set.of("ao-thun", "ao-thun-2", "ao-thun-3");
        assertEquals("ao-thun-4", SlugGenerator.unique("ao-thun", taken::contains));
        assertEquals("quan-jean", SlugGenerator.unique("quan-jean", taken::contains));
    }
}
