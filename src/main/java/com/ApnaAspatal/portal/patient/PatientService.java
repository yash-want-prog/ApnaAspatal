package com.ApnaAspatal.portal.patient;
import org.springframework.stereotype.Service;
@Service 
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public Patient createPatient(Patient patient) {
        return patientRepository.save(patient);
    }

    public Patient getPatientById(Long id){
        return patientRepository.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
    }


}