package com.example.backend.storage;

/**
 * Decides the image type from the file's own leading bytes (magic numbers). The client's filename, extension and
 * Content-Type are never consulted. SVG (and anything else) is not recognised, so it is rejected.
 */
public final class ImageSniffer {

    public record Detected(String extension, String contentType) {
    }

    private ImageSniffer() {
    }

    /** @return the detected type, or {@code null} when the bytes are not JPEG, PNG or WebP */
    public static Detected detect(byte[] b) {
        if (b == null) return null;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new Detected("jpg", "image/jpeg");
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return new Detected("png", "image/png");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new Detected("webp", "image/webp");
        }
        return null;
    }

    /** Content type for a key produced by this application (extension is one of the three we generate). */
    public static String contentTypeForExtension(String extension) {
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
