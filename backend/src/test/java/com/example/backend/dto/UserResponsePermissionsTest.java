package com.example.backend.dto;

import com.example.backend.entity.Permission;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserResponsePermissionsTest {

    private static Role role(long id, String name, String... codes) {
        Role r = new Role(id, name);
        Set<Permission> perms = new HashSet<>();
        long pid = id * 100;
        for (String c : codes) perms.add(new Permission(pid++, c));
        r.setPermissions(perms);
        return r;
    }

    @Test
    void permissions_areUnionOfAllRoles_deduplicated_andSorted() {
        User u = new User();
        u.setId(1L);
        u.setRoles(new HashSet<>(Set.of(
                role(1, Role.MANAGER, "product:view", "order:view"),
                role(2, Role.ADMIN, "product:view", "audit:view"))));

        assertEquals(List.of("audit:view", "order:view", "product:view"), UserResponse.from(u).permissions);
    }

    @Test
    void customer_hasEmptyPermissions_andRolesAreReported() {
        User u = new User();
        u.setId(2L);
        u.setRoles(new HashSet<>(Set.of(role(1, Role.CUSTOMER))));

        UserResponse r = UserResponse.from(u);
        assertTrue(r.permissions.isEmpty());
        assertEquals(List.of("CUSTOMER"), r.roles);
    }
}
