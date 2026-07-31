package com.sahha.organisation.service.organisationservice;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.organisation.dto.request.CreateOrganisationRequest;
import com.sahha.organisation.dto.response.OrganisationPageResponse;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.entity.Organisation;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationOutboxEvent;
import com.sahha.organisation.event.OrganisationEventMapper;
import com.sahha.organisation.exception.OrganisationNotFoundException;
import com.sahha.organisation.mapper.OrganisationMapper;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.OrganisationRepository;

@Service
public class OrganisationService {

	private final OrganisationRepository organisationRepository;
	private final OrganisationAuditEventRepository auditRepository;
	private final OrganisationOutboxEventRepository outboxRepository;
	private final OrganisationMapper organisationMapper;
	private final OrganisationEventMapper eventMapper;
	private final Clock clock;

	public OrganisationService(
			OrganisationRepository organisationRepository,
			OrganisationAuditEventRepository auditRepository,
			OrganisationOutboxEventRepository outboxRepository,
			OrganisationMapper organisationMapper,
			OrganisationEventMapper eventMapper,
			Clock clock) {
		this.organisationRepository = organisationRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.organisationMapper = organisationMapper;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional
	public OrganisationResponse create(
			CreateOrganisationRequest request,
			UUID actorUserId,
			String requestId) {
		String timeZone = validTimeZone(request.timeZone());
		Instant occurredAt = clock.instant();
		Organisation organisation = Organisation.createActive(
				request.name(),
				request.legalName(),
				request.type(),
				request.contactEmail(),
				request.phoneNumber(),
				request.address(),
				request.city(),
				request.region(),
				request.postalCode(),
				request.countryCode(),
				timeZone,
				actorUserId,
				occurredAt);
		organisationRepository.saveAndFlush(organisation);
		OrganisationAuditEvent audit = auditRepository.save(
				OrganisationAuditEvent.created(
						organisation,
						actorUserId,
						requestId,
						occurredAt));
		outboxRepository.save(
				OrganisationOutboxEvent.pending(
						audit,
						eventMapper.toPayload(organisation, audit)));
		return organisationMapper.toResponse(organisation);
	}

	@Transactional(readOnly = true)
	public OrganisationResponse find(UUID organisationId) {
		return organisationRepository.findById(organisationId)
				.map(organisationMapper::toResponse)
				.orElseThrow(OrganisationNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public OrganisationPageResponse list(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException(
					"page must be non-negative and size must be between 1 and 100");
		}
		Page<Organisation> organisations = organisationRepository.findAll(
				PageRequest.of(
						page,
						size,
						Sort.by(
								Sort.Order.asc("normalizedName"),
								Sort.Order.asc("id"))));
		return new OrganisationPageResponse(
				organisations.getContent()
						.stream()
						.map(organisationMapper::toResponse)
						.toList(),
				organisations.getNumber(),
				organisations.getSize(),
				organisations.getTotalElements(),
				organisations.getTotalPages());
	}

	private static String validTimeZone(String value) {
		try {
			return ZoneId.of(value.strip()).getId();
		}
		catch (DateTimeException | NullPointerException invalidTimeZone) {
			throw new IllegalArgumentException("timeZone is invalid");
		}
	}
}
