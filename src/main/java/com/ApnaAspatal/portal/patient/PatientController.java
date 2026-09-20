package com.ApnaAspatal.portal.patient;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ApnaAspatal.portal.patient.dto.PatientRequest;
import com.ApnaAspatal.portal.patient.dto.PatientResponse;

/**
 * HTTP entry point for patient resources.
 *
 * <p>Translates HTTP into service calls and back. It holds no business rules.
 * The {@code Patient} entity never leaves this class.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse create(@Valid @RequestBody PatientRequest request) {
        Patient patient = new Patient(
                request.fullName(),
                request.email(),
                request.phone(),
                request.dateOfBirth());

        return toResponse(patientService.createPatient(patient));
    }

    @GetMapping("/{id}")
    public PatientResponse getById(@PathVariable Long id) {
        return toResponse(patientService.getPatientById(id));
    }

    /**
     * Maps the persistence type to the API type. Private and static: it is this
     * controller's business how a Patient is presented, and it needs no state.
     */
    private static PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getFullName(),
                patient.getEmail(),
                patient.getPhone(),
                patient.getDateOfBirth());
    }
}
