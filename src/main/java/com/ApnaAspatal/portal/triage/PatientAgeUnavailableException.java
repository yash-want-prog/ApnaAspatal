package com.ApnaAspatal.portal.triage;

/**
 * Thrown when a triage session cannot be evaluated because the patient's age
 * cannot be determined.
 *
 * <p>Age is a required input to the rule engine. Guessing it - for example,
 * treating an unknown age as adult - would be a clinical decision made by
 * accident, so evaluation is refused instead.
 */
public class PatientAgeUnavailableException extends RuntimeException {

    private PatientAgeUnavailableException(String message) {
        super(message);
    }

    public static PatientAgeUnavailableException missingDateOfBirth(Long sessionId, Long patientId) {
        return new PatientAgeUnavailableException(
                "Cannot evaluate triage session " + sessionId
                        + ": patient " + patientId + " has no date of birth recorded");
    }

    public static PatientAgeUnavailableException dateOfBirthInFuture(Long sessionId, Long patientId) {
        return new PatientAgeUnavailableException(
                "Cannot evaluate triage session " + sessionId
                        + ": patient " + patientId + " has a date of birth after the evaluation date");
    }
}
