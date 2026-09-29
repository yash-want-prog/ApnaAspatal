package com.ApnaAspatal.portal.patient;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.ApnaAspatal.portal.auth.AppUser;

/**
 * A patient registered with SmartTriage.
 *
 * <p>This is a persistence type, not an API type. It is mapped to the
 * {@code patients} table and must never be returned directly from a controller -
 * DTOs will sit at the API boundary once we build the Patient endpoints.
 */
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    private String email;

    private String phone;

    private LocalDate dateOfBirth;

    /**
     * The account this record belongs to; only its owner can read or change it.
     *
     * <p>Nullable in the schema only because records created before accounts
     * existed have no owner. Those match no owner's queries, so they are
     * unreachable rather than exposed. Every new record is given an owner.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields, so a no-argument constructor must exist.
     * Kept protected so application code uses the constructor below instead.
     */
    protected Patient() {
    }

    public Patient(String fullName, String email, String phone, LocalDate dateOfBirth) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
    }

    /**
     * Null until the entity has been persisted - the database assigns the value.
     * There is deliberately no setter: application code must never choose an id.
     */
    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    /**
     * Package-private: only {@code PatientService} assigns an owner, once, when
     * the record is created. There is no way to transfer a record.
     */
    void assignOwner(AppUser owner) {
        this.owner = owner;
    }
}
