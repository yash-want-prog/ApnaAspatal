package com.ApnaAspatal.portal.patient;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Patient}.
 *
 * <p>Spring Data generates the implementation at startup; there is no class to
 * write. The type arguments say what this repository manages: {@code Patient}
 * entities, identified by a {@code Long}.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {
}
