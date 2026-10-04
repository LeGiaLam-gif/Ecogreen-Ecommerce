package com.example.backend.security;

import com.example.backend.entity.RefreshToken;
import com.example.backend.repository.RefreshTokenRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Mockito-backed in-memory stand-in for RefreshTokenRepository (same atomic "claim" semantics as the SQL). */
final class InMemoryRefreshTokens {

    final Map<Long, RefreshToken> rows = new LinkedHashMap<>();
    final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AtomicLong ids = new AtomicLong(0);

    InMemoryRefreshTokens() {
        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            if (t.getId() == null) {
                try {
                    var f = RefreshToken.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(t, ids.incrementAndGet());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            rows.put(t.getId(), t);
            return t;
        });
        lenient().when(repository.findByTokenHash(anyString())).thenAnswer(inv ->
                rows.values().stream().filter(t -> t.getTokenHash().equals(inv.getArgument(0))).findFirst());
        lenient().when(repository.revokeIfActive(anyLong())).thenAnswer(inv -> {
            RefreshToken t = rows.get(inv.<Long>getArgument(0));
            if (t == null || t.isRevoked()) return 0;
            t.setRevoked(true);
            return 1;
        });
        lenient().when(repository.markReplaced(anyLong(), anyLong())).thenAnswer(inv -> {
            RefreshToken t = rows.get(inv.<Long>getArgument(0));
            if (t == null) return 0;
            t.setReplacedById(inv.getArgument(1));
            return 1;
        });
        lenient().when(repository.revokeFamily(anyString())).thenAnswer(inv -> {
            int n = 0;
            for (RefreshToken t : rows.values()) {
                if (t.getFamilyId().equals(inv.getArgument(0)) && !t.isRevoked()) { t.setRevoked(true); n++; }
            }
            return n;
        });
    }

    Optional<RefreshToken> byRaw(String raw) {
        return rows.values().stream().filter(t -> t.getTokenHash().equals(RefreshTokenService.sha256Hex(raw))).findFirst();
    }
}
