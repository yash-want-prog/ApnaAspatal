package com.ApnaAspatal.portal.patient;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ApnaAspatal.portal.auth.AppUserRepository;
import com.ApnaAspatal.portal.auth.CurrentUser;

/**
 * Patient records, always scoped to the signed-in user.
 *
 * <p>A record belonging to someone else is reported as not found rather than
 * forbidden, so a caller cannot even learn that it exists.
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUser currentUser;

    public PatientService(PatientRepository patientRepository, AppUserRepository appUserRepository,
            CurrentUser currentUser) {
        this.patientRepository = patientRepository;
        this.appUserRepository = appUserRepository;
        this.currentUser = currentUser;
    }

    /** The new record belongs to the signed-in user. */
    public Patient createPatient(Patient patient) {
        patient.assignOwner(appUserRepository.getReferenceById(currentUser.id()));
        return patientRepository.save(patient);
    }

    public Patient getPatientById(Long id){
        return patientRepository.findByIdAndOwnerId(id, currentUser.id())
                .orElseThrow(() -> new PatientNotFoundException(id));
    }

    /** Only the signed-in user's own records. */
    public List<Patient> getAllPatients() {
        return patientRepository.findByOwnerIdOrderByIdAsc(currentUser.id());
    }

    public Patient updatePatient(Long id, Patient updatedPatient) {
        Patient existing = getPatientById(id);

        existing.setFullName(updatedPatient.getFullName());
        existing.setEmail(updatedPatient.getEmail());
        existing.setPhone(updatedPatient.getPhone());
        existing.setDateOfBirth(updatedPatient.getDateOfBirth());

        return patientRepository.save(existing);
    }

    public void deletePatient(Long id) {
        Patient existing = getPatientById(id);
        patientRepository.delete(existing);
    }


}
