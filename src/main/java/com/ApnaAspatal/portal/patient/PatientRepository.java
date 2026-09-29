package com.ApnaAspatal.portal.patient;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Patient}.
 *
 * <p>Spring Data generates the implementation at startup; there is no class to
 * write. Application code reads patients only through the owner-scoped queries
 * below, so the ownership check happens in the database query itself and an id
 * alone never reaches a record.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByIdAndOwnerId(Long id, Long ownerId);

    List<Patient> findByOwnerIdOrderByIdAsc(Long ownerId);
}
