package com.ApnaAspatal.portal.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link AppUser}. Usernames are always passed already
 * normalised - see {@link AuthService#normaliseUsername(String)}.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
