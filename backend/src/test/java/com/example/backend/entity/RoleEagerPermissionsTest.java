package com.example.backend.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import org.hibernate.annotations.BatchSize;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the design decision behind UserResponse/JwtService: they read Role.permissions without a guaranteed persistence
 * context, so the mapping must stay EAGER (no LazyInitializationException) and batched (no per-role/per-permission queries).
 * User.roles must also remain EAGER (unchanged from B01-P2).
 */
class RoleEagerPermissionsTest {

    @Test
    void rolePermissions_isEagerAndBatched() throws Exception {
        var field = Role.class.getDeclaredField("permissions");
        assertEquals(FetchType.EAGER, field.getAnnotation(ManyToMany.class).fetch());
        assertNotNull(field.getAnnotation(BatchSize.class));
    }

    @Test
    void userRoles_remainsEager() throws Exception {
        var field = User.class.getDeclaredField("roles");
        assertEquals(FetchType.EAGER, field.getAnnotation(ManyToMany.class).fetch());
    }
}
