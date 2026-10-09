package com.example.backend.controller;

import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.storage.ImageSnifferTest;
import com.example.backend.storage.LocalDiskStorageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** GET /api/v1/files/{key} against the real local-disk store in a temp directory. */
class FileControllerTest {

    @TempDir Path temp;

    private MockMvc mvc(LocalDiskStorageClient storage) {
        FileController controller = new FileController();
        ReflectionTestUtils.setField(controller, "storage", storage);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void get_servesStoredImage_withNosniffAndImageContentType() throws Exception {
        LocalDiskStorageClient storage = new LocalDiskStorageClient(temp.resolve("uploads").toString());
        String key = storage.store(ImageSnifferTest.png(), "png");

        MockHttpServletResponse r = mvc(storage).perform(get("/api/v1/files/" + key)).andReturn().getResponse();

        assertEquals(200, r.getStatus());
        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
        assertEquals("image/png", r.getContentType());
        assertArrayEquals(ImageSnifferTest.png(), r.getContentAsByteArray());
    }

    @Test
    void get_unknownMalformedOrTraversingKey_is404_andNeverLeaksOtherFiles() throws Exception {
        LocalDiskStorageClient storage = new LocalDiskStorageClient(temp.resolve("uploads").toString());
        storage.store(ImageSnifferTest.png(), "png");
        Files.writeString(temp.resolve("secret.png"), "top secret");
        MockMvc mvc = mvc(storage);

        for (String key : new String[]{"0123456789abcdef0123456789abcdef.png", "evil.svg", "..%2Fsecret.png",
                "%2e%2e%2fsecret.png", "secret.png", "0123456789abcdef0123456789abcdef.html"}) {
            MockHttpServletResponse r = mvc.perform(get("/api/v1/files/" + key)).andReturn().getResponse();
            assertEquals(404, r.getStatus(), key);
            assertEquals(false, r.getContentAsString().contains("top secret"), key);
        }
    }
}
