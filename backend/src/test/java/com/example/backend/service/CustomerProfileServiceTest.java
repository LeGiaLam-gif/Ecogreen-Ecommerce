package com.example.backend.service;

import com.example.backend.dto.CustomerProfileRequest;
import com.example.backend.entity.User;
import com.example.backend.exception.ConflictException;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private CustomerProfileService service;

    @Test
    void updateChangesOnlyTheRequestedUsersProfile_andTrimsOptionalPhone() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("unchanged-hash");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.update(7L, new CustomerProfileRequest(" Alice ", " alice@example.com ", " 0901234567 "));

        assertEquals("Alice", updated.getUsername());
        assertEquals("alice@example.com", updated.getEmail());
        assertEquals("0901234567", updated.getPhone());
        assertEquals("unchanged-hash", updated.getPassword());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void updateRejectsAnotherAccountsEmailWithoutSaving() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.update(7L,
                new CustomerProfileRequest("alice", "taken@example.com", null)));

        assertNull(user.getPhone());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}
