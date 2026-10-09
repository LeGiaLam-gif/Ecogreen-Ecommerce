package com.example.backend.controller;

import com.example.backend.storage.ImageSniffer;
import com.example.backend.storage.ObjectStorageClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Serves uploaded images. The only non-enveloped success response of B02 (binary body). Errors keep the standard
 * envelope (an unknown or malformed key is 404). Responses are never sniffed by the browser.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @Autowired private ObjectStorageClient storage;

    @GetMapping("/{key:.+}")
    public ResponseEntity<Resource> get(@PathVariable String key) {
        Resource file = storage.load(key); // validates the key format and keeps the path inside the upload dir
        String extension = key.substring(key.lastIndexOf('.') + 1);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ImageSniffer.contentTypeForExtension(extension)))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(file);
    }
}
