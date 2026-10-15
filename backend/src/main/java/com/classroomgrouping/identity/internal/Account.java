package com.classroomgrouping.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "app_account")
public class Account {
    @Id private UUID id;
    @Column(nullable = false, length = 100, unique = true) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false, length = 20) private String role;

    protected Account() {}

    public Account(String username, String passwordHash, String role) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() { return username; }
    public UUID getId() { return id; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
}
