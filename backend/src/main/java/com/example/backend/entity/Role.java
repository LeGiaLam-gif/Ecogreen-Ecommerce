package com.example.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String MANAGER = "MANAGER";
    public static final String ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * B01-P3. EAGER on purpose (UserResponse.from(User) and JwtService read it outside any guaranteed persistence
     * context, so a lazy collection would risk LazyInitializationException; RoleEagerPermissionsTest guards this).
     * @BatchSize loads the permissions of all roles already in the session with ONE query, never one per role/permission.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @BatchSize(size = 50)
    private Set<Permission> permissions = new HashSet<>();

    public Role() {}

    public Role(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
}
