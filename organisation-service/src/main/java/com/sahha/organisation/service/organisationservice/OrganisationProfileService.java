package com.sahha.organisation.service.organisationservice;

import java.time.Clock;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.organisation.dto.request.UpdateOrganisationProfileRequest;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.entity.Organisation;
import com.sahha.organisation.entity.OrganisationAuditEvent;
import com.sahha.organisation.entity.OrganisationOutboxEvent;
import com.sahha.organisation.entity.OrganisationRole;
import com.sahha.organisation.event.OrganisationEventMapper;
import com.sahha.organisation.exception.OrganisationNotFoundException;
import com.sahha.organisation.mapper.OrganisationMapper;
import com.sahha.organisation.repository.OrganisationAuditEventRepository;
import com.sahha.organisation.repository.OrganisationOutboxEventRepository;
import com.sahha.organisation.repository.OrganisationRepository;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationContextService;

@Service
public class OrganisationProfileService {
    private final OrganisationRepository repository;
    private final OrganisationContextService contexts;
    private final OrganisationMapper mapper;
    private final OrganisationAuditEventRepository audit;
    private final OrganisationOutboxEventRepository outbox;
    private final OrganisationEventMapper events;
    private final Clock clock;
    public OrganisationProfileService(OrganisationRepository repository, OrganisationContextService contexts,
            OrganisationMapper mapper, OrganisationAuditEventRepository audit,
            OrganisationOutboxEventRepository outbox, OrganisationEventMapper events, Clock clock) {
        this.repository = repository; this.contexts = contexts; this.mapper = mapper;
        this.audit = audit; this.outbox = outbox; this.events = events; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public OrganisationResponse get(UUID organisationId, UUID actorId) {
        contexts.requireActiveRole(organisationId, actorId, OrganisationRole.ORGANIZATION_ADMIN);
        return mapper.toResponse(repository.findById(organisationId).orElseThrow(OrganisationNotFoundException::new));
    }
    @Transactional
    public OrganisationResponse update(UUID organisationId, UUID actorId,
            UpdateOrganisationProfileRequest request, String requestId) {
        contexts.requireActiveRole(organisationId, actorId, OrganisationRole.ORGANIZATION_ADMIN);
        Organisation organisation = repository.findById(organisationId).orElseThrow(OrganisationNotFoundException::new);
        if (organisation.getVersion() != request.version()) {
            throw new ObjectOptimisticLockingFailureException(Organisation.class, organisationId);
        }
        var now = clock.instant();
        organisation.updateProfile(request.name(), request.contactEmail(), request.phoneNumber(),
                request.address(), request.city(), request.region(), request.postalCode(),
                request.countryCode(), actorId, now);
        repository.saveAndFlush(organisation);
        var recorded = audit.save(OrganisationAuditEvent.profileUpdated(organisation, actorId, requestId, now));
        outbox.save(OrganisationOutboxEvent.pending(recorded, events.toPayload(organisation, recorded)));
        return mapper.toResponse(organisation);
    }
}
