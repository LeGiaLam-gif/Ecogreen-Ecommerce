package com.example.backend.service;

import com.example.backend.entity.Address;
import com.example.backend.repository.AddressRepository;
import com.example.backend.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * B08 test helper: a Mockito-backed in-memory AddressRepository plus an AddressService wired to it. Like the PostgreSQL
 * partial unique index {@code uq_addresses_one_default}, a save that would leave two defaults for one user FAILS, so a
 * service that forgets to clear the old default first is caught here.
 */
final class InMemoryAddresses {

    final Map<Long, Address> rows = new LinkedHashMap<>();
    final AddressRepository repository = mock(AddressRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final AddressService service = new AddressService();
    private final AtomicLong ids = new AtomicLong(0);

    InMemoryAddresses() {
        lenient().when(users.lockRowById(anyLong())).thenAnswer(inv -> Optional.of(inv.<Long>getArgument(0)));
        lenient().when(repository.saveAndFlush(any(Address.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        lenient().when(repository.save(any(Address.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        lenient().when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<Long>getArgument(0))));
        lenient().when(repository.findAllByUser(anyLong())).thenAnswer(inv -> forUser(inv.getArgument(0)).stream()
                .sorted((x, y) -> {
                    int byDefault = Boolean.compare(y.isDefault(), x.isDefault()); // default first
                    return byDefault != 0 ? byDefault : Long.compare(y.getId(), x.getId()); // then newest first
                })
                .toList());
        lenient().when(repository.countByUserId(anyLong())).thenAnswer(inv -> (long) forUser(inv.getArgument(0)).size());
        lenient().when(repository.findFirstByUserIdOrderByIdDesc(anyLong())).thenAnswer(inv -> forUser(inv.getArgument(0)).stream()
                .max(Comparator.comparing(Address::getId)));
        lenient().when(repository.clearDefaultsExcept(anyLong(), anyLong(), any(LocalDateTime.class))).thenAnswer(inv -> {
            int n = 0;
            for (Address a : forUser(inv.getArgument(0))) {
                if (a.isDefault() && !a.getId().equals(inv.<Long>getArgument(1))) { a.setDefault(false); n++; }
            }
            return n;
        });
        lenient().doAnswer(inv -> { rows.remove(inv.<Address>getArgument(0).getId()); return null; })
                .when(repository).delete(any(Address.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "addressRepository", repository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userRepository", users);
    }

    private Address store(Address a) {
        if (a.getId() == null) a.setId(ids.incrementAndGet());
        rows.put(a.getId(), a);
        // The partial unique index: at most one default per user.
        long defaults = forUser(a.getUserId()).stream().filter(Address::isDefault).count();
        if (defaults > 1) throw new IllegalStateException("uq_addresses_one_default violated for user " + a.getUserId());
        return a;
    }

    List<Address> forUser(Long userId) {
        return rows.values().stream().filter(a -> userId.equals(a.getUserId())).toList();
    }

    long defaultsOf(Long userId) {
        return forUser(userId).stream().filter(Address::isDefault).count();
    }
}
