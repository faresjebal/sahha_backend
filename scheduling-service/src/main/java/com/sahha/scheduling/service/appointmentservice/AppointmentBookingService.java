package com.sahha.scheduling.service.appointmentservice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.client.organisation.SchedulingDoctorDirectoryClient;
import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.client.patient.PatientRegistrationResource;
import com.sahha.scheduling.client.patient.PatientRegistryClient;
import com.sahha.scheduling.client.patient.PatientSchedulingContextResource;
import com.sahha.scheduling.dto.request.BookAppointmentRequest;
import com.sahha.scheduling.entity.AppointmentActorType;
import com.sahha.scheduling.service.availabilityservice.SchedulingAccessService;

@Service
public class AppointmentBookingService {

	private final SchedulingAccessService accessService;
	private final SchedulingDoctorDirectoryClient doctorDirectoryClient;
	private final PatientRegistryClient patientRegistryClient;
	private final AppointmentBookingPersistenceService persistenceService;

	public AppointmentBookingService(
			SchedulingAccessService accessService,
			SchedulingDoctorDirectoryClient doctorDirectoryClient,
			PatientRegistryClient patientRegistryClient,
			AppointmentBookingPersistenceService persistenceService) {
		this.accessService = accessService;
		this.doctorDirectoryClient = doctorDirectoryClient;
		this.patientRegistryClient = patientRegistryClient;
		this.persistenceService = persistenceService;
	}

	public AppointmentBookingResult book(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId,
			BookAppointmentRequest request) {
		OrganisationContextResource actor = accessService.requireBooking(
				organisationId, actorUserId, accessToken);
		PatientRegistrationResource patient = patientRegistryClient
				.findActiveRegistration(
						organisationId,
						request.patientRegistrationId(),
						accessToken);
		SchedulingDoctorResource doctor = doctorDirectoryClient.findActiveDoctor(
				organisationId,
				request.doctorUserId(),
				accessToken);
		return persistenceService.book(
				organisationId,
				actorUserId,
				AppointmentActorType.STAFF,
				actor.membershipId(),
				patient,
				doctor,
				requestId,
				request);
	}

	public AppointmentBookingResult bookMine(
			UUID actorUserId,
			String accessToken,
			String requestId,
			BookAppointmentRequest request) {
		PatientSchedulingContextResource context = patientRegistryClient
				.findOwnActiveRegistration(
						request.patientRegistrationId(), actorUserId, accessToken);
		SchedulingDoctorResource doctor = doctorDirectoryClient
				.findPatientVisibleActiveDoctor(
						context.organisationId(), request.doctorUserId(), accessToken);
		PatientRegistrationResource patient = new PatientRegistrationResource(
				context.registrationId(),
				context.patientId(),
				context.organisationId(),
				context.registrationStatus());
		return persistenceService.book(
				context.organisationId(),
				actorUserId,
				AppointmentActorType.PATIENT,
				null,
				patient,
				doctor,
				requestId,
				request);
	}
}
