package com.example.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.backend.entity.Role;
import com.example.backend.repository.RoleRepository;

/**
 * Seeds ONLY structural system configuration (the two roles the app needs to
 * function). Deliberately does NOT create any business data: no demo users, no
 * demo products, no demo orders/payments. The application must start and behave
 * correctly with empty products/categories tables (see spec section "Database
 * data policy" / "empty database behavior").
 *
 * To try the admin area: register a normal account through the app, then
 * manually add the ADMIN role for that user, e.g.: INSERT INTO user_roles
 * (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username =
 * 'your_username' AND r.name = 'ADMIN';
 */
@Configuration
public class DataLoader {

    @Bean
    public CommandLineRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            if (roleRepository.findByName(Role.USER).isEmpty()) {
                roleRepository.save(new Role(null, Role.USER));
            }
            if (roleRepository.findByName(Role.ADMIN).isEmpty()) {
                roleRepository.save(new Role(null, Role.ADMIN));
            }
        };
    }
}
