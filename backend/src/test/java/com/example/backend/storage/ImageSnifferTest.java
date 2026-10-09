package com.example.backend.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageSnifferTest {

    static byte[] jpeg() { return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'}; }
    static byte[] png() { return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13}; }
    static byte[] webp() { return new byte[]{'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '}; }

    @Test
    void detect_jpegPngWebp_byMagicBytes() {
        assertEquals("jpg", ImageSniffer.detect(jpeg()).extension());
        assertEquals("image/jpeg", ImageSniffer.detect(jpeg()).contentType());
        assertEquals("png", ImageSniffer.detect(png()).extension());
        assertEquals("webp", ImageSniffer.detect(webp()).extension());
    }

    @Test
    void detect_svgHtmlExeGifTextAndTruncated_areRejected() {
        assertNull(ImageSniffer.detect("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect("<?xml version=\"1.0\"?><svg/>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect("<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect(new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0}));
        assertNull(ImageSniffer.detect("GIF89a......".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect("just text".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8}));     // truncated JPEG header
        assertNull(ImageSniffer.detect(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'})); // RIFF but not WebP
        assertNull(ImageSniffer.detect(new byte[0]));
        assertNull(ImageSniffer.detect(null));
        assertNotNull(ImageSniffer.detect(jpeg()));
    }
}
