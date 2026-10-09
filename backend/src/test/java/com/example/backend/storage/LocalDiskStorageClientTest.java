package com.example.backend.storage;

import com.example.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDiskStorageClientTest {

    @TempDir Path temp;

    private LocalDiskStorageClient client() {
        return new LocalDiskStorageClient(temp.resolve("uploads").toString());
    }

    @Test
    void store_generatesRandomKey_andLoadReturnsTheSameBytes() throws Exception {
        LocalDiskStorageClient c = client();
        String k1 = c.store(ImageSnifferTest.png(), "png");
        String k2 = c.store(ImageSnifferTest.png(), "png");

        assertTrue(k1.matches("[0-9a-f]{32}\\.png"), k1);
        assertFalse(k1.equals(k2));
        Resource r = c.load(k1);
        assertArrayEquals(ImageSnifferTest.png(), r.getInputStream().readAllBytes());
        assertTrue(Files.exists(temp.resolve("uploads").resolve(k1)));
    }

    @Test
    void store_rejectsAnyExtensionOtherThanTheThreeImageTypes() {
        LocalDiskStorageClient c = client();
        for (String ext : new String[]{"svg", "html", "jsp", "../x", "png/../../a", ""}) {
            assertThrows(IllegalArgumentException.class, () -> c.store(new byte[]{1}, ext));
        }
    }

    @Test
    void upload_pathTraversalKey_isRejected() throws Exception {
        LocalDiskStorageClient c = client();
        c.store(ImageSnifferTest.png(), "png"); // creates the upload directory
        Files.writeString(temp.resolve("secret.png"), "top secret");   // a file OUTSIDE the upload directory

        String[] bad = {
                "../secret.png", "..\\secret.png", "..%2Fsecret.png", "%2e%2e/secret.png", "/etc/passwd",
                temp.resolve("secret.png").toString(), "a/../../secret.png", "....//secret.png",
                "0123456789abcdef0123456789abcdef.png/../../secret.png", "0123456789abcdef0123456789abcdef.svg",
                "0123456789ABCDEF0123456789ABCDEF.png", "short.png", "", " ", "0123456789abcdef0123456789abcdef.png\0.txt"
        };
        for (String key : bad) {
            assertThrows(ResourceNotFoundException.class, () -> c.load(key), "load must reject: " + key);
            c.delete(key); // must be a silent no-op
        }
        assertTrue(Files.exists(temp.resolve("secret.png")), "a file outside the upload dir must never be touched");
        assertThrows(ResourceNotFoundException.class, () -> c.load(null));
    }

    @Test
    void load_wellFormedButUnknownKey_isNotFound_andDeleteRemovesTheFile() {
        LocalDiskStorageClient c = client();
        assertThrows(ResourceNotFoundException.class, () -> c.load("0123456789abcdef0123456789abcdef.jpg"));

        String key = c.store(ImageSnifferTest.jpeg(), "jpg");
        c.delete(key);
        assertThrows(ResourceNotFoundException.class, () -> c.load(key));
        assertEquals(0, temp.resolve("uploads").toFile().list().length);
    }
}
