package com.sahha.scheduling.service.appointmentservice;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.client.patient.PatientRegistryClient;
import com.sahha.scheduling.client.patient.PatientSchedulingContextResource;
import com.sahha.scheduling.dto.response.AppointmentResponse;
import com.sahha.scheduling.dto.response.PatientAppointmentResponse;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.exception.AppointmentNotFoundException;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;
import com.sahha.scheduling.mapper.AppointmentMapper;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.service.availabilityservice.SchedulingAccessService;

@Service
public class AppointmentQueryService {

	private static final Duration MAXIMUM_RANGE = Duration.ofDays(31);
	private final SchedulingAccessService accessService;
	private final PatientRegistryClient patientRegistryClient;
	private final AppointmentRepository appointmentRepository;
	private final AppointmentMapper mapper;

	public AppointmentQueryService(
			SchedulingAccessService accessService,
			PatientRegistryClient patientRegistryClient,
			AppointmentRepository appointmentRepository,
			AppointmentMapper mapper) {
		this.accessService = accessService;
		this.patientRegistryClient = patientRegistryClient;
		this.appointmentRepository = appointmentRepository;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public List<PatientAppointmentResponse> listMine(
			UUID registrationId,
			UUID actorUserId,
			String accessToken,
			Instant from,
			Instant to) {
		validateRange(from, to);
		PatientSchedulingContextResource context = patientRegistryClient
				.findOwnActiveRegistration(registrationId, actorUserId, accessToken);
		return appointmentRepository.findPatientAppointments(
				context.organisationId(), registrationId, from, to)
				.stream()
				.map(mapper::patientResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<AppointmentResponse> list(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			Instant from,
			Instant to) {
		validateRange(from, to);
		OrganisationContextResource actor = accessService.requireAppointmentAccess(
				organisationId, actorUserId, accessToken);
		boolean operational = isOperational(actor);
		List<Appointment> appointments = operational
				? appointmentRepository.findOrganisationAppointments(
						organisationId, from, to)
				: appointmentRepository.findDoctorAppointments(
						organisationId, actorUserId, from, to);
		return appointments.stream().map(mapper::response).toList();
	}

	@Transactional(readOnly = true)
	public AppointmentResponse find(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			UUID appointmentId) {
		OrganisationContextResource actor = accessService.requireAppointmentAccess(
				organisationId, actorUserId, accessToken);
		Appointment appointment = appointmentRepository
				.findByIdAndOrganisationId(appointmentId, organisationId)
				.orElseThrow(AppointmentNotFoundException::new);
		if (!isOperational(actor)
				&& !appointment.getDoctorUserId().equals(actorUserId)) {
			throw new SchedulingAccessDeniedException();
		}
		return mapper.response(appointment);
	}

	private static boolean isOperational(OrganisationContextResource actor) {
		return actor.roles().contains("RECEPTIONIST")
				|| actor.roles().contains("ORGANIZATION_ADMIN");
	}

	private static void validateRange(Instant from, Instant to) {
		if (from == null || to == null || !to.isAfter(from)
				|| Duration.between(from, to).compareTo(MAXIMUM_RANGE) > 0) {
			throw new IllegalArgumentException(
					"Appointment ranges must be positive and at most 31 days.");
		}
	}
}
