package com.example.backend.storage;

import com.example.backend.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local-disk store under {@code ecogreen.upload.dir} (outside the classpath, git-ignored). File names are random and
 * generated here; a key is accepted only if it matches the exact generated format, and the resolved path must stay
 * inside the upload directory, so {@code ..}, separators, absolute paths and encoded tricks can never leave it.
 */
@Component
public class LocalDiskStorageClient implements ObjectStorageClient {

    static final Pattern KEY = Pattern.compile("[0-9a-f]{32}\\.(jpg|png|webp)");

    private final Path root;

    public LocalDiskStorageClient(@Value("${ecogreen.upload.dir:./uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String extension) {
        if (!extension.matches("jpg|png|webp")) {
            throw new IllegalArgumentException("unsupported extension");
        }
        String key = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(key), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        Path file = resolve(key);
        if (file == null || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Không tìm thấy tệp.");
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        Path file = resolve(key);
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return the file path for a well-formed key inside the root, otherwise null */
    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) return null;
        Path file = root.resolve(key).normalize();
        return file.startsWith(root) ? file : null;
    }
}
