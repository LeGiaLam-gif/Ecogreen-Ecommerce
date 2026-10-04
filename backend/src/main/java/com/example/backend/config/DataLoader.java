package com.example.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.backend.entity.Role;
import com.example.backend.repository.RoleRepository;

/**
 * Seeds ONLY structural system configuration (the three roles the app needs to
 * function: CUSTOMER, MANAGER, ADMIN - B01-P3). Deliberately does NOT create any
 * business data: no demo users, no demo products, no demo orders/payments, and it
 * never re-creates the legacy USER role (renamed to CUSTOMER by V5). The permission
 * catalogue and role->permission mapping are seeded by V4-V6, not here. The
 * application must start and behave correctly with empty products/categories
 * tables (see spec section "Database data policy" / "empty database behavior").
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
            for (String name : new String[] { Role.CUSTOMER, Role.MANAGER, Role.ADMIN }) {
                if (roleRepository.findByName(name).isEmpty()) {
                    roleRepository.save(new Role(null, name));
                }
            }
        };
    }
}
