package com.example.backend.config;

import com.example.backend.entity.Role;
import com.example.backend.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataLoaderTest {

    @Test
    void dataLoader_doesNotRecreateUserRole_andSeedsTheThreeNewRoles() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        when(roles.findByName(any())).thenReturn(Optional.empty());

        new DataLoader().initRoles(roles).run();

        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roles, times(3)).save(saved.capture());
        assertEquals(java.util.List.of("CUSTOMER", "MANAGER", "ADMIN"), saved.getAllValues().stream().map(Role::getName).toList());
        assertFalse(saved.getAllValues().stream().anyMatch(r -> "USER".equals(r.getName())));
        verify(roles, never()).findByName("USER");
    }

    @Test
    void dataLoader_existingRoles_areNotDuplicated() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        when(roles.findByName(any())).thenReturn(Optional.of(new Role(1L, "x")));
        when(roles.findByName("MANAGER")).thenReturn(Optional.empty());

        new DataLoader().initRoles(roles).run();

        verify(roles, times(1)).save(any(Role.class));
        verify(roles).save(org.mockito.ArgumentMatchers.argThat(r -> Role.MANAGER.equals(r.getName())));
        verify(roles, never()).findByName(eq("USER"));
    }
}
