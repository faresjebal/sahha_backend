package com.sahha.patient.service.patientduplicateservice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.patient.dto.response.DuplicateCandidateResponse;
import com.sahha.patient.dto.response.DuplicateCheckResponse;
import com.sahha.patient.dto.response.DuplicateMatchReason;
import com.sahha.patient.entity.PatientIdentity;
import com.sahha.patient.entity.PatientOrganisationRegistration;
import com.sahha.patient.repository.PatientIdentityRepository;
import com.sahha.patient.repository.PatientOrganisationRegistrationRepository;
import com.sahha.patient.service.patientidentifierservice.ProtectedPatientIdentifier;

@Service
public class DuplicateDetectionService {

	private final PatientIdentityRepository identityRepository;
	private final PatientOrganisationRegistrationRepository registrationRepository;

	public DuplicateDetectionService(
			PatientIdentityRepository identityRepository,
			PatientOrganisationRegistrationRepository registrationRepository) {
		this.identityRepository = identityRepository;
		this.registrationRepository = registrationRepository;
	}

	public DuplicateCheckResponse detect(
			UUID organisationId,
			String normalizedFirstName,
			String normalizedLastName,
			LocalDate dateOfBirth,
			String normalizedPhoneNumber,
			String normalizedEmail,
			ProtectedPatientIdentifier identifier,
			UUID excludedPatientId) {
		Map<UUID, CandidateAccumulator> matches = new LinkedHashMap<>();
		if (identifier != null) {
			identityRepository
					.findByIdentifierTypeAndIdentifierFingerprint(
							identifier.type(),
							identifier.fingerprint())
					.filter(patient -> !patient.getId().equals(excludedPatientId))
					.ifPresent(patient -> add(
							matches,
							patient,
							DuplicateMatchReason.STRONG_IDENTIFIER));
		}
		identityRepository
				.findAllByNormalizedFirstNameAndNormalizedLastNameAndDateOfBirth(
						normalizedFirstName,
						normalizedLastName,
						dateOfBirth)
				.stream()
				.filter(patient -> !patient.getId().equals(excludedPatientId))
				.forEach(patient -> add(
						matches,
						patient,
						DuplicateMatchReason.NAME_AND_DATE_OF_BIRTH));
		registrationRepository
				.findAllByNormalizedPhoneNumber(normalizedPhoneNumber)
				.stream()
				.map(PatientOrganisationRegistration::getPatient)
				.filter(patient -> !patient.getId().equals(excludedPatientId))
				.forEach(patient -> add(
						matches,
						patient,
						DuplicateMatchReason.PHONE_NUMBER));
		if (normalizedEmail != null) {
			registrationRepository
					.findAllByNormalizedEmail(normalizedEmail)
					.stream()
					.map(PatientOrganisationRegistration::getPatient)
					.filter(patient -> !patient.getId().equals(excludedPatientId))
					.forEach(patient -> add(
							matches,
							patient,
							DuplicateMatchReason.EMAIL));
		}

		Map<UUID, PatientOrganisationRegistration> localRegistrations =
				new LinkedHashMap<>();
		if (!matches.isEmpty()) {
			registrationRepository
					.findAllByOrganisationIdAndPatientIdIn(
							organisationId,
							matches.keySet())
					.forEach(registration -> localRegistrations.put(
							registration.getPatient().getId(),
							registration));
		}

		List<DuplicateCandidateResponse> candidates = matches.values().stream()
				.map(candidate -> response(
						candidate,
						localRegistrations.get(candidate.patient.getId())))
				.sorted(Comparator
						.comparing(DuplicateCandidateResponse::exactStrongIdentifierMatch)
						.reversed()
						.thenComparing(
								DuplicateCandidateResponse::score,
								Comparator.reverseOrder())
						.thenComparing(DuplicateCandidateResponse::patientId))
				.toList();
		return new DuplicateCheckResponse(
				!candidates.isEmpty(),
				candidates.stream().anyMatch(
						DuplicateCandidateResponse::exactStrongIdentifierMatch),
				candidates);
	}

	private static void add(
			Map<UUID, CandidateAccumulator> matches,
			PatientIdentity patient,
			DuplicateMatchReason reason) {
		matches.computeIfAbsent(
				patient.getId(),
				ignored -> new CandidateAccumulator(patient))
				.reasons.add(reason);
	}

	private static DuplicateCandidateResponse response(
			CandidateAccumulator candidate,
			PatientOrganisationRegistration localRegistration) {
		List<DuplicateMatchReason> reasons = new ArrayList<>(candidate.reasons);
		int score = reasons.contains(DuplicateMatchReason.STRONG_IDENTIFIER)
				? 100
				: reasons.stream().mapToInt(DuplicateDetectionService::weight).sum();
		PatientIdentity patient = candidate.patient;
		return new DuplicateCandidateResponse(
				patient.getId(),
				maskName(patient.getFirstName(), patient.getLastName()),
				patient.getDateOfBirth().getYear(),
				patient.getSex(),
				patient.getIdentifierLastFour() == null
						? null
						: "****" + patient.getIdentifierLastFour(),
				score,
				reasons.contains(DuplicateMatchReason.STRONG_IDENTIFIER),
				List.copyOf(reasons),
				localRegistration != null,
				localRegistration == null ? null : localRegistration.getId());
	}

	private static int weight(DuplicateMatchReason reason) {
		return switch (reason) {
			case NAME_AND_DATE_OF_BIRTH -> 45;
			case PHONE_NUMBER -> 30;
			case EMAIL -> 25;
			case STRONG_IDENTIFIER -> 100;
		};
	}

	private static String maskName(String firstName, String lastName) {
		return firstName.substring(0, 1) + "*** "
				+ lastName.substring(0, 1) + "***";
	}

	private static final class CandidateAccumulator {

		private final PatientIdentity patient;
		private final EnumSet<DuplicateMatchReason> reasons =
				EnumSet.noneOf(DuplicateMatchReason.class);

		private CandidateAccumulator(PatientIdentity patient) {
			this.patient = patient;
		}
	}
}
