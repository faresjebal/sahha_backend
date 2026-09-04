package com.sahha.scheduling.service.appointmentservice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.client.organisation.SchedulingDoctorDirectoryClient;
import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.dto.request.AppointmentCommandRequest;
import com.sahha.scheduling.dto.request.ReasonedAppointmentCommandRequest;
import com.sahha.scheduling.dto.request.RescheduleAppointmentRequest;
import com.sahha.scheduling.dto.response.AppointmentResponse;
import com.sahha.scheduling.service.availabilityservice.SchedulingAccessService;

@Service
public class AppointmentCommandService {

	private final SchedulingAccessService accessService;
	private final SchedulingDoctorDirectoryClient doctorDirectoryClient;
	private final AppointmentTransitionPersistenceService persistenceService;

	public AppointmentCommandService(
			SchedulingAccessService accessService,
			SchedulingDoctorDirectoryClient doctorDirectoryClient,
			AppointmentTransitionPersistenceService persistenceService) {
		this.accessService = accessService;
		this.doctorDirectoryClient = doctorDirectoryClient;
		this.persistenceService = persistenceService;
	}

	public AppointmentResponse confirm(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		return persistenceService.confirm(
				organisationId, actorUserId, actor, requestId,
				appointmentId, request);
	}

	public AppointmentResponse reject(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			ReasonedAppointmentCommandRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		return persistenceService.reject(
				organisationId, actorUserId, actor, requestId,
				appointmentId, request);
	}

	public AppointmentResponse cancel(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			ReasonedAppointmentCommandRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		return persistenceService.cancel(
				organisationId, actorUserId, actor, requestId,
				appointmentId, request);
	}

	public AppointmentResponse checkIn(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		return persistenceService.checkIn(
				organisationId, actorUserId, actor, requestId,
				appointmentId, request);
	}

	public AppointmentResponse start(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		return persistenceService.start(
				organisationId, actorUserId, actor, requestId,
				appointmentId, request);
	}

	public AppointmentResponse complete(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		return persistenceService.complete(
				organisationId, actorUserId, actor, requestId,
				appointmentId, request);
	}

	public AppointmentResponse markNoShow(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			AppointmentCommandRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		return persistenceService.markNoShow(
				organisationId, actorUserId, actor, requestId,
				appointmentId, request);
	}

	public AppointmentResponse reschedule(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			UUID appointmentId,
			RescheduleAppointmentRequest request) {
		OrganisationContextResource actor = actor(
				organisationId, actorUserId, accessToken);
		AppointmentReschedulePreparation preparation =
				persistenceService.prepareReschedule(
				organisationId, actorUserId, actor, appointmentId,
				request.commandRequestId(), request.reason(), request.startsAt());
		if (preparation.replay() != null) {
			return preparation.replay();
		}
		SchedulingDoctorResource doctor = doctorDirectoryClient.findActiveDoctor(
				organisationId, preparation.doctorUserId(), accessToken);
		return persistenceService.reschedule(
				organisationId, actorUserId, actor, doctor, requestId,
				appointmentId, request);
	}

	private OrganisationContextResource actor(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		return accessService.requireAppointmentAccess(
				organisationId, actorUserId, accessToken);
	}
}
