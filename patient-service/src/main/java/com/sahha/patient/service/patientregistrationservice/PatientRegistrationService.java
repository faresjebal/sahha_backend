package com.sahha.patient.service.patientregistrationservice;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.patient.dto.request.CreatePatientRegistrationRequest;
import com.sahha.patient.dto.request.DuplicateCheckRequest;
import com.sahha.patient.dto.request.PatientIdentifierRequest;
import com.sahha.patient.dto.request.UpdatePatientRegistrationRequest;
import com.sahha.patient.dto.response.DuplicateCandidateResponse;
import com.sahha.patient.dto.response.DuplicateCheckResponse;
import com.sahha.patient.dto.response.PatientAdministrativePageResponse;
import com.sahha.patient.dto.response.PatientAdministrativeResponse;
import com.sahha.patient.dto.response.PatientAuditHistoryResponse;
import com.sahha.patient.entity.DuplicateDecision;
import com.sahha.patient.entity.PatientAuditEvent;
import com.sahha.patient.entity.PatientAuditEventType;
import com.sahha.patient.entity.PatientIdentity;
import com.sahha.patient.entity.PatientOrganisationRegistration;
import com.sahha.patient.entity.PatientOutboxEvent;
import com.sahha.patient.event.PatientEventMapper;
import com.sahha.patient.exception.ConcurrentPatientModificationException;
import com.sahha.patient.exception.PatientRegistrationConflictException;
import com.sahha.patient.exception.PatientRegistrationNotFoundException;
import com.sahha.patient.exception.PossibleDuplicateException;
import com.sahha.patient.exception.SharedPatientIdentityConflictException;
import com.sahha.patient.mapper.PatientAdministrativeMapper;
import com.sahha.patient.repository.PatientAuditEventRepository;
import com.sahha.patient.repository.PatientIdentityRepository;
import com.sahha.patient.repository.PatientOrganisationRegistrationRepository;
import com.sahha.patient.repository.PatientOutboxEventRepository;
import com.sahha.patient.service.patientaccessservice.PatientAccessService;
import com.sahha.patient.service.patientduplicateservice.DuplicateDetectionService;
import com.sahha.patient.service.patientidentifierservice.PatientIdentifierProtectionService;
import com.sahha.patient.service.patientidentifierservice.ProtectedPatientIdentifier;
import com.sahha.patient.service.patientidentityservice.PatientDataNormalizer;

@Service
public class PatientRegistrationService {

	private static final int MAXIMUM_PATIENT_AGE = 130;

	private final PatientIdentityRepository identityRepository;
	private final PatientOrganisationRegistrationRepository registrationRepository;
	private final PatientAuditEventRepository auditRepository;
	private final PatientOutboxEventRepository outboxRepository;
	private final PatientAccessService accessService;
	private final DuplicateDetectionService duplicateDetectionService;
	private final PatientIdentifierProtectionService identifierProtectionService;
	private final PatientDataNormalizer normalizer;
	private final PatientAdministrativeMapper mapper;
	private final PatientEventMapper eventMapper;
	private final Clock clock;

	public PatientRegistrationService(
			PatientIdentityRepository identityRepository,
			PatientOrganisationRegistrationRepository registrationRepository,
			PatientAuditEventRepository auditRepository,
			PatientOutboxEventRepository outboxRepository,
			PatientAccessService accessService,
			DuplicateDetectionService duplicateDetectionService,
			PatientIdentifierProtectionService identifierProtectionService,
			PatientDataNormalizer normalizer,
			PatientAdministrativeMapper mapper,
			PatientEventMapper eventMapper,
			Clock clock) {
		this.identityRepository = identityRepository;
		this.registrationRepository = registrationRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.accessService = accessService;
		this.duplicateDetectionService = duplicateDetectionService;
		this.identifierProtectionService = identifierProtectionService;
		this.normalizer = normalizer;
		this.mapper = mapper;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional
	public DuplicateCheckResponse checkDuplicates(
			UUID organisationId,
			DuplicateCheckRequest request,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		requireAccess(organisationId, actorUserId, accessToken);
		validateDateOfBirth(request.dateOfBirth());
		NormalizedProbe probe = probe(
				request.firstName(),
				request.lastName(),
				request.dateOfBirth(),
				request.phoneNumber(),
				request.email(),
				request.identifier());
		DuplicateCheckResponse result = detect(organisationId, probe, null);
		recordDirectoryActivity(
				organisationId,
				actorUserId,
				PatientAuditEventType.PATIENT_DUPLICATE_CHECKED,
				Map.of(
						"candidateCount", result.candidates().size(),
						"exactStrongIdentifierMatch",
						result.exactStrongIdentifierMatch()),
				requestId,
				clock.instant());
		return result;
	}

	@Transactional
	public PatientAdministrativeResponse create(
			UUID organisationId,
			CreatePatientRegistrationRequest request,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		requireAccess(organisationId, actorUserId, accessToken);
		validateDateOfBirth(request.dateOfBirth());
		validateEmergencyContact(
				request.emergencyContactName(),
				request.emergencyContactPhone());
		NormalizedProbe probe = probe(
				request.firstName(),
				request.lastName(),
				request.dateOfBirth(),
				request.phoneNumber(),
				request.email(),
				request.identifier());
		DuplicateCheckResponse duplicates = detect(organisationId, probe, null);
		PatientIdentity identity = resolveIdentity(
				request,
				probe,
				duplicates,
				actorUserId,
				clock.instant());
		if (registrationRepository.findByOrganisationIdAndPatientId(
				organisationId,
				identity.getId()).isPresent()) {
			throw new PatientRegistrationConflictException();
		}
		Instant occurredAt = clock.instant();
		PatientOrganisationRegistration registration =
				PatientOrganisationRegistration.create(
						identity,
						organisationId,
						display(request.phoneNumber()),
						probe.normalizedPhoneNumber(),
						displayOptional(request.email()),
						probe.normalizedEmail(),
						display(request.address()),
						displayOptional(request.city()),
						displayOptional(request.region()),
						displayOptional(request.postalCode()),
						request.countryCode(),
						displayOptional(request.emergencyContactName()),
						displayOptional(request.emergencyContactPhone()),
						displayOptional(request.emergencyContactRelationship()),
						displayOptional(request.preferredLanguage()),
						displayOptional(request.accessibilityNeeds()),
						actorUserId,
						occurredAt);
		try {
			if (identity.getVersion() == 0
					&& !identityRepository.existsById(identity.getId())) {
				identityRepository.saveAndFlush(identity);
			}
			registrationRepository.saveAndFlush(registration);
		}
		catch (DataIntegrityViolationException conflict) {
			throw new PatientRegistrationConflictException();
		}
		recordMutation(
				registration,
				PatientAuditEventType.PATIENT_REGISTERED,
				actorUserId,
				Map.of(
						"linkedExistingIdentity",
						request.duplicateDecision()
								== DuplicateDecision.LINK_EXISTING,
						"duplicateDecision",
						request.duplicateDecision() == null
								? "NO_CANDIDATE"
								: request.duplicateDecision().name(),
						"duplicateDecisionReason",
						request.duplicateDecisionReason() == null
								? "NOT_REQUIRED"
								: request.duplicateDecisionReason().name()),
				requestId,
				occurredAt);
		return mapper.toResponse(registration);
	}

	@Transactional
	public PatientAdministrativePageResponse list(
			UUID organisationId,
			String query,
			int page,
			int size,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		requireAccess(organisationId, actorUserId, accessToken);
		validatePage(page, size);
		String normalizedQuery = normalizer.search(query);
		Page<PatientOrganisationRegistration> registrations =
				registrationRepository.searchAdministrativeDirectory(
						organisationId,
						normalizedQuery,
						PageRequest.of(
								page,
								size,
								Sort.by(
										Sort.Order.desc("createdAt"),
										Sort.Order.asc("id"))));
		recordDirectoryActivity(
				organisationId,
				actorUserId,
				PatientAuditEventType.PATIENT_DIRECTORY_SEARCHED,
				Map.of(
						"queryProvided", !normalizedQuery.isEmpty(),
						"resultCount", registrations.getNumberOfElements()),
				requestId,
				clock.instant());
		return new PatientAdministrativePageResponse(
				registrations.getContent().stream().map(mapper::toSummary).toList(),
				registrations.getNumber(),
				registrations.getSize(),
				registrations.getTotalElements(),
				registrations.getTotalPages());
	}

	@Transactional
	public PatientAdministrativeResponse find(
			UUID organisationId,
			UUID registrationId,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		requireAccess(organisationId, actorUserId, accessToken);
		PatientOrganisationRegistration registration = findScoped(
				organisationId,
				registrationId);
		auditRepository.save(PatientAuditEvent.registrationActivity(
				registration,
				actorUserId,
				PatientAuditEventType.PATIENT_ADMINISTRATIVE_PROFILE_VIEWED,
				Map.of(),
				requestId,
				clock.instant()));
		return mapper.toResponse(registration);
	}

	@Transactional
	public PatientAdministrativeResponse update(
			UUID organisationId,
			UUID registrationId,
			UpdatePatientRegistrationRequest request,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		requireAccess(organisationId, actorUserId, accessToken);
		validateDateOfBirth(request.dateOfBirth());
		validateEmergencyContact(
				request.emergencyContactName(),
				request.emergencyContactPhone());
		if (request.identifier() != null && request.removeIdentifier()) {
			throw new IllegalArgumentException(
					"identifier and removeIdentifier cannot both be supplied");
		}
		PatientOrganisationRegistration registration = findScoped(
				organisationId,
				registrationId);
		PatientIdentity identity = registration.getPatient();
		if (registration.getVersion() != request.registrationVersion()
				|| identity.getVersion() != request.identityVersion()) {
			throw new ConcurrentPatientModificationException();
		}
		ProtectedPatientIdentifier identifier = resolveUpdatedIdentifier(
				identity,
				request.identifier(),
				request.removeIdentifier());
		NormalizedProbe probe = new NormalizedProbe(
				normalizer.name(request.firstName()),
				normalizer.name(request.lastName()),
				request.dateOfBirth(),
				normalizer.phone(request.phoneNumber()),
				normalizer.email(request.email()),
				identifier);
		DuplicateCheckResponse duplicates = detect(
				organisationId,
				probe,
				identity.getId());
		validateUpdateDuplicateDecision(request, duplicates);
		Set<String> changedFields = changedFields(
				registration,
				identity,
				request,
				probe);
		if (changedFields.isEmpty()) {
			return mapper.toResponse(registration);
		}
		boolean identityChanged = changedFields.stream().anyMatch(
				field -> field.startsWith("identity."));
		if (identityChanged
				&& registrationRepository.countByPatientId(identity.getId()) > 1) {
			throw new SharedPatientIdentityConflictException();
		}
		Instant occurredAt = clock.instant();
		if (identityChanged) {
			identity.updateIdentity(
					display(request.firstName()),
					probe.normalizedFirstName(),
					display(request.lastName()),
					probe.normalizedLastName(),
					request.dateOfBirth(),
					request.sex(),
					identifier == null ? null : identifier.type(),
					identifier == null ? null : identifier.fingerprint(),
					identifier == null ? null : identifier.lastFour(),
					identifier == null ? null : identifier.countryCode(),
					actorUserId,
					occurredAt);
		}
		registration.updateAdministrativeDetails(
				display(request.phoneNumber()),
				probe.normalizedPhoneNumber(),
				displayOptional(request.email()),
				probe.normalizedEmail(),
				display(request.address()),
				displayOptional(request.city()),
				displayOptional(request.region()),
				displayOptional(request.postalCode()),
				request.countryCode(),
				displayOptional(request.emergencyContactName()),
				displayOptional(request.emergencyContactPhone()),
				displayOptional(request.emergencyContactRelationship()),
				displayOptional(request.preferredLanguage()),
				displayOptional(request.accessibilityNeeds()),
				actorUserId,
				occurredAt);
		try {
			identityRepository.flush();
			registrationRepository.saveAndFlush(registration);
		}
		catch (ObjectOptimisticLockingFailureException concurrentChange) {
			throw new ConcurrentPatientModificationException();
		}
		catch (DataIntegrityViolationException conflict) {
			throw new PatientRegistrationConflictException();
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("changedFields", List.copyOf(changedFields));
		metadata.put(
				"duplicateDecision",
				request.duplicateDecision() == null
						? "NO_CANDIDATE"
						: request.duplicateDecision().name());
		if (request.duplicateDecisionReason() != null) {
			metadata.put(
					"duplicateDecisionReason",
					request.duplicateDecisionReason().name());
		}
		recordMutation(
				registration,
				PatientAuditEventType.PATIENT_ADMINISTRATIVE_PROFILE_UPDATED,
				actorUserId,
				metadata,
				requestId,
				occurredAt);
		return mapper.toResponse(registration);
	}

	@Transactional
	public List<PatientAuditHistoryResponse> history(
			UUID organisationId,
			UUID registrationId,
			UUID actorUserId,
			String accessToken) {
		requireAccess(organisationId, actorUserId, accessToken);
		findScoped(organisationId, registrationId);
		return auditRepository
				.findAllByOrganisationIdAndResourceIdOrderByOccurredAtDescIdDesc(
						organisationId,
						registrationId)
				.stream()
				.map(mapper::toHistory)
				.toList();
	}

	private PatientIdentity resolveIdentity(
			CreatePatientRegistrationRequest request,
			NormalizedProbe probe,
			DuplicateCheckResponse duplicates,
			UUID actorUserId,
			Instant occurredAt) {
		if (duplicates.candidates().isEmpty()) {
			if (request.duplicateDecision() != null
					|| request.duplicateDecisionReason() != null
					|| request.selectedPatientId() != null) {
				throw new PatientRegistrationConflictException();
			}
			return newIdentity(request, probe, actorUserId, occurredAt);
		}
		if (request.duplicateDecision() == null
				|| request.duplicateDecisionReason() == null) {
			throw new PossibleDuplicateException(duplicates);
		}
		if (request.duplicateDecision() == DuplicateDecision.CREATE_NEW) {
			if (duplicates.exactStrongIdentifierMatch()
					|| request.selectedPatientId() != null) {
				throw new PatientRegistrationConflictException();
			}
			return newIdentity(request, probe, actorUserId, occurredAt);
		}
		if (request.selectedPatientId() == null) {
			throw new PatientRegistrationConflictException();
		}
		DuplicateCandidateResponse selected = duplicates.candidates().stream()
				.filter(candidate -> candidate.patientId().equals(
						request.selectedPatientId()))
				.findFirst()
				.orElseThrow(PatientRegistrationConflictException::new);
		if (duplicates.exactStrongIdentifierMatch()
				&& !selected.exactStrongIdentifierMatch()) {
			throw new PatientRegistrationConflictException();
		}
		if (selected.registeredInActiveOrganisation()) {
			throw new PatientRegistrationConflictException();
		}
		return identityRepository.findById(selected.patientId())
				.orElseThrow(PatientRegistrationConflictException::new);
	}

	private PatientIdentity newIdentity(
			CreatePatientRegistrationRequest request,
			NormalizedProbe probe,
			UUID actorUserId,
			Instant occurredAt) {
		ProtectedPatientIdentifier identifier = probe.identifier();
		return PatientIdentity.create(
				display(request.firstName()),
				probe.normalizedFirstName(),
				display(request.lastName()),
				probe.normalizedLastName(),
				request.dateOfBirth(),
				request.sex(),
				identifier == null ? null : identifier.type(),
				identifier == null ? null : identifier.fingerprint(),
				identifier == null ? null : identifier.lastFour(),
				identifier == null ? null : identifier.countryCode(),
				actorUserId,
				occurredAt);
	}

	private void validateUpdateDuplicateDecision(
			UpdatePatientRegistrationRequest request,
			DuplicateCheckResponse duplicates) {
		if (duplicates.candidates().isEmpty()) {
			if (request.duplicateDecision() != null
					|| request.duplicateDecisionReason() != null) {
				throw new PatientRegistrationConflictException();
			}
			return;
		}
		if (duplicates.exactStrongIdentifierMatch()) {
			throw new PatientRegistrationConflictException();
		}
		if (request.duplicateDecision() != DuplicateDecision.CREATE_NEW
				|| request.duplicateDecisionReason() == null) {
			throw new PossibleDuplicateException(duplicates);
		}
	}

	private Set<String> changedFields(
			PatientOrganisationRegistration registration,
			PatientIdentity identity,
			UpdatePatientRegistrationRequest request,
			NormalizedProbe probe) {
		Set<String> fields = new LinkedHashSet<>();
		changed(fields, "identity.firstName", identity.getFirstName(),
				display(request.firstName()));
		changed(fields, "identity.lastName", identity.getLastName(),
				display(request.lastName()));
		changed(fields, "identity.dateOfBirth", identity.getDateOfBirth(),
				request.dateOfBirth());
		changed(fields, "identity.sex", identity.getSex(), request.sex());
		ProtectedPatientIdentifier identifier = probe.identifier();
		changed(fields, "identity.identifierType", identity.getIdentifierType(),
				identifier == null ? null : identifier.type());
		changed(fields, "identity.identifierFingerprint",
				identity.getIdentifierFingerprint(),
				identifier == null ? null : identifier.fingerprint());
		changed(fields, "contact.phoneNumber", registration.getPhoneNumber(),
				display(request.phoneNumber()));
		changed(fields, "contact.email", registration.getEmail(),
				displayOptional(request.email()));
		changed(fields, "contact.address", registration.getAddress(),
				display(request.address()));
		changed(fields, "contact.city", registration.getCity(),
				displayOptional(request.city()));
		changed(fields, "contact.region", registration.getRegion(),
				displayOptional(request.region()));
		changed(fields, "contact.postalCode", registration.getPostalCode(),
				displayOptional(request.postalCode()));
		changed(fields, "contact.countryCode", registration.getCountryCode(),
				request.countryCode());
		changed(fields, "emergencyContact.name",
				registration.getEmergencyContactName(),
				displayOptional(request.emergencyContactName()));
		changed(fields, "emergencyContact.phone",
				registration.getEmergencyContactPhone(),
				displayOptional(request.emergencyContactPhone()));
		changed(fields, "emergencyContact.relationship",
				registration.getEmergencyContactRelationship(),
				displayOptional(request.emergencyContactRelationship()));
		changed(fields, "support.preferredLanguage",
				registration.getPreferredLanguage(),
				displayOptional(request.preferredLanguage()));
		changed(fields, "support.accessibilityNeeds",
				registration.getAccessibilityNeeds(),
				displayOptional(request.accessibilityNeeds()));
		return fields;
	}

	private ProtectedPatientIdentifier resolveUpdatedIdentifier(
			PatientIdentity identity,
			PatientIdentifierRequest request,
			boolean removeIdentifier) {
		if (removeIdentifier) {
			return null;
		}
		if (request != null) {
			return identifierProtectionService.protect(request);
		}
		if (identity.getIdentifierType() == null) {
			return null;
		}
		return new ProtectedPatientIdentifier(
				identity.getIdentifierType(),
				identity.getIdentifierFingerprint(),
				identity.getIdentifierLastFour(),
				identity.getIdentifierCountryCode());
	}

	private NormalizedProbe probe(
			String firstName,
			String lastName,
			LocalDate dateOfBirth,
			String phoneNumber,
			String email,
			PatientIdentifierRequest identifier) {
		return new NormalizedProbe(
				normalizer.name(firstName),
				normalizer.name(lastName),
				dateOfBirth,
				normalizer.phone(phoneNumber),
				normalizer.email(email),
				identifierProtectionService.protect(identifier));
	}

	private DuplicateCheckResponse detect(
			UUID organisationId,
			NormalizedProbe probe,
			UUID excludedPatientId) {
		return duplicateDetectionService.detect(
				organisationId,
				probe.normalizedFirstName(),
				probe.normalizedLastName(),
				probe.dateOfBirth(),
				probe.normalizedPhoneNumber(),
				probe.normalizedEmail(),
				probe.identifier(),
				excludedPatientId);
	}

	private void recordMutation(
			PatientOrganisationRegistration registration,
			PatientAuditEventType eventType,
			UUID actorUserId,
			Map<String, Object> metadata,
			String requestId,
			Instant occurredAt) {
		PatientAuditEvent audit = auditRepository.save(
				PatientAuditEvent.registrationActivity(
						registration,
						actorUserId,
						eventType,
						metadata,
						requestId,
						occurredAt));
		outboxRepository.save(PatientOutboxEvent.pending(
				audit,
				eventMapper.toPayload(registration, audit)));
	}

	private void recordDirectoryActivity(
			UUID organisationId,
			UUID actorUserId,
			PatientAuditEventType eventType,
			Map<String, Object> metadata,
			String requestId,
			Instant occurredAt) {
		auditRepository.save(PatientAuditEvent.directoryActivity(
				organisationId,
				actorUserId,
				eventType,
				metadata,
				requestId,
				occurredAt));
	}

	private PatientOrganisationRegistration findScoped(
			UUID organisationId,
			UUID registrationId) {
		return registrationRepository
				.findByIdAndOrganisationId(registrationId, organisationId)
				.orElseThrow(PatientRegistrationNotFoundException::new);
	}

	private void requireAccess(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		accessService.requireAdministrativeAccess(
				organisationId,
				actorUserId,
				accessToken);
	}

	private void validateDateOfBirth(LocalDate dateOfBirth) {
		LocalDate today = LocalDate.now(clock);
		if (dateOfBirth.isAfter(today)
				|| dateOfBirth.isBefore(today.minusYears(MAXIMUM_PATIENT_AGE))) {
			throw new IllegalArgumentException("dateOfBirth is invalid");
		}
	}

	private void validateEmergencyContact(String name, String phone) {
		if ((name == null || name.isBlank()) != (phone == null || phone.isBlank())) {
			throw new IllegalArgumentException(
					"emergency contact name and phone must be supplied together");
		}
		if (phone != null && !phone.isBlank()) {
			normalizer.phone(phone);
		}
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException(
					"page must be non-negative and size must be between 1 and 100");
		}
	}

	private static String display(String value) {
		if (value == null) {
			throw new IllegalArgumentException("required value is missing");
		}
		String displayed = value.strip().replaceAll("\\s+", " ");
		if (displayed.isEmpty()) {
			throw new IllegalArgumentException("required value is blank");
		}
		return displayed;
	}

	private static String displayOptional(String value) {
		return value == null || value.isBlank() ? null : display(value);
	}

	private static void changed(
			Set<String> fields,
			String field,
			Object before,
			Object after) {
		if (!Objects.equals(before, after)) {
			fields.add(field);
		}
	}

	private record NormalizedProbe(
			String normalizedFirstName,
			String normalizedLastName,
			LocalDate dateOfBirth,
			String normalizedPhoneNumber,
			String normalizedEmail,
			ProtectedPatientIdentifier identifier) {
	}
}
