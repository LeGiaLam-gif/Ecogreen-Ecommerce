package com.example.backend.storage;

import org.springframework.core.io.Resource;

/**
 * Where uploaded binaries live. The only implementation today is {@link LocalDiskStorageClient}; a cloud store can
 * replace it without touching controllers or services. Keys are always generated server-side.
 */
public interface ObjectStorageClient {

    /** Stores the bytes under a new random key ({@code <32 hex>.<extension>}) and returns that key. */
    String store(byte[] content, String extension);

    /**
     * Resolves a key to its file.
     * @throws com.example.backend.exception.ResourceNotFoundException for a malformed, traversing or unknown key
     */
    Resource load(String key);

    /** Removes the object if present; a malformed key or a missing file is ignored. */
    void delete(String key);
}
