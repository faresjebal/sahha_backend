package com.sahha.patient.service.patientaccountservice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.patient.client.auth.AuthAccountClient;
import com.sahha.patient.client.auth.AuthAccountResource;
import com.sahha.patient.dto.request.LinkPatientAccountRequest;
import com.sahha.patient.dto.response.MyPatientRegistrationResponse;
import com.sahha.patient.dto.response.PatientAccountLinkResponse;
import com.sahha.patient.dto.response.PatientSchedulingContextResponse;
import com.sahha.patient.entity.PatientAccountLink;
import com.sahha.patient.entity.PatientAuditEvent;
import com.sahha.patient.entity.PatientAuditEventType;
import com.sahha.patient.entity.PatientOrganisationRegistration;
import com.sahha.patient.entity.PatientOutboxEvent;
import com.sahha.patient.entity.PatientRegistrationStatus;
import com.sahha.patient.event.PatientEventMapper;
import com.sahha.patient.exception.PatientAccountLinkConflictException;
import com.sahha.patient.exception.PatientAccountLinkNotFoundException;
import com.sahha.patient.repository.PatientAccountLinkRepository;
import com.sahha.patient.repository.PatientAuditEventRepository;
import com.sahha.patient.repository.PatientOrganisationRegistrationRepository;
import com.sahha.patient.repository.PatientOutboxEventRepository;

@Service
public class PatientAccountService {

	private final PatientAccountLinkRepository linkRepository;
	private final PatientOrganisationRegistrationRepository registrationRepository;
	private final PatientAuditEventRepository auditRepository;
	private final PatientOutboxEventRepository outboxRepository;
	private final AuthAccountClient authAccountClient;
	private final PatientEventMapper eventMapper;
	private final Clock clock;

	public PatientAccountService(
			PatientAccountLinkRepository linkRepository,
			PatientOrganisationRegistrationRepository registrationRepository,
			PatientAuditEventRepository auditRepository,
			PatientOutboxEventRepository outboxRepository,
			AuthAccountClient authAccountClient,
			PatientEventMapper eventMapper,
			Clock clock) {
		this.linkRepository = linkRepository;
		this.registrationRepository = registrationRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.authAccountClient = authAccountClient;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional
	public PatientAccountLinkResponse link(
			UUID actorUserId,
			String accessToken,
			LinkPatientAccountRequest request,
			String requestId) {
		AuthAccountResource account = authAccountClient.currentAccount(accessToken);
		if (!actorUserId.equals(account.id()) || !account.eligibleForPatientLink()) {
			throw new PatientAccountLinkNotFoundException();
		}
		PatientOrganisationRegistration registration = registrationRepository
				.findByOrganisationIdAndMedicalRecordNumber(
						request.organisationId(),
						request.medicalRecordNumber().strip().toUpperCase(Locale.ROOT))
				.filter(candidate -> candidate.getStatus() == PatientRegistrationStatus.ACTIVE)
				.filter(candidate -> candidate.getPatient().getDateOfBirth()
						.equals(request.dateOfBirth()))
				.filter(candidate -> candidate.getNormalizedEmail() != null)
				.filter(candidate -> candidate.getNormalizedEmail().equals(
						account.email().strip().toLowerCase(Locale.ROOT)))
				.orElseThrow(PatientAccountLinkNotFoundException::new);

		PatientAccountLink existingByUser = linkRepository
				.findByAuthUserId(actorUserId).orElse(null);
		if (existingByUser != null) {
			if (!existingByUser.getPatientId().equals(registration.getPatient().getId())) {
				throw new PatientAccountLinkConflictException();
			}
			return response(existingByUser);
		}
		if (linkRepository.findByPatientId(registration.getPatient().getId()).isPresent()) {
			throw new PatientAccountLinkConflictException();
		}

		Instant occurredAt = clock.instant();
		PatientAccountLink link = PatientAccountLink.link(
				registration.getPatient().getId(), actorUserId, occurredAt);
		try {
			linkRepository.saveAndFlush(link);
		}
		catch (DataIntegrityViolationException conflict) {
			throw new PatientAccountLinkConflictException();
		}
		PatientAuditEvent audit = auditRepository.saveAndFlush(
				PatientAuditEvent.accountLinkActivity(
						link,
						registration.getOrganisationId(),
						PatientAuditEventType.PATIENT_ACCOUNT_LINKED,
						Map.of("registrationId", registration.getId()),
						requestId,
						occurredAt));
		outboxRepository.save(PatientOutboxEvent.pending(
				audit,
				eventMapper.toAccountLinkPayload(link, audit)));
		return response(link);
	}

	@Transactional(readOnly = true)
	public List<MyPatientRegistrationResponse> registrations(UUID actorUserId) {
		PatientAccountLink link = requireLink(actorUserId);
		return registrationRepository.findAllByPatientIdOrderByCreatedAt(
				link.getPatientId()).stream()
				.map(registration -> new MyPatientRegistrationResponse(
						registration.getId(),
						registration.getOrganisationId(),
						registration.getMedicalRecordNumber(),
						registration.getStatus(),
						registration.getPatient().getFirstName(),
						registration.getPatient().getLastName()))
				.toList();
	}

	@Transactional(readOnly = true)
	public PatientSchedulingContextResponse schedulingContext(
			UUID actorUserId,
			UUID registrationId) {
		PatientAccountLink link = requireLink(actorUserId);
		PatientOrganisationRegistration registration = registrationRepository
				.findById(registrationId)
				.filter(candidate -> candidate.getPatient().getId()
						.equals(link.getPatientId()))
				.filter(candidate -> candidate.getStatus() == PatientRegistrationStatus.ACTIVE)
				.orElseThrow(PatientAccountLinkNotFoundException::new);
		return new PatientSchedulingContextResponse(
				registration.getId(),
				registration.getPatient().getId(),
				registration.getOrganisationId(),
				registration.getStatus(),
				link.getAuthUserId());
	}

	private PatientAccountLink requireLink(UUID actorUserId) {
		return linkRepository.findByAuthUserId(actorUserId)
				.orElseThrow(PatientAccountLinkNotFoundException::new);
	}

	private static PatientAccountLinkResponse response(PatientAccountLink link) {
		return new PatientAccountLinkResponse(
				link.getId(), link.getAuthUserId(), link.getLinkedAt());
	}
}
