package com.ApnaAspatal.portal.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An account that can sign in. It owns the patient records it creates - its own,
 * or those of people it cares for - and can reach only those.
 *
 * <p>Mapped to {@code app_users} because {@code user} is a reserved word in
 * PostgreSQL. The password is stored only as a salted hash.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored lower-cased, so sign-in is case-insensitive. */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** An encoded hash with its algorithm prefix, e.g. {@code {bcrypt}...}; never the password. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields.
     */
    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, LocalDateTime createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
