package com.sahha.scheduling.service.appointmentservice;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.dto.response.ClinicalAppointmentContextResponse;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.exception.AppointmentNotFoundException;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.service.availabilityservice.SchedulingAccessService;

@Service
public class ClinicalAppointmentContextService {

	private final AppointmentRepository appointmentRepository;
	private final SchedulingAccessService accessService;

	public ClinicalAppointmentContextService(
			AppointmentRepository appointmentRepository,
			SchedulingAccessService accessService) {
		this.appointmentRepository = appointmentRepository;
		this.accessService = accessService;
	}

	@Transactional(readOnly = true)
	public ClinicalAppointmentContextResponse resolve(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			UUID appointmentId) {
		OrganisationContextResource membership = accessService.requireDoctor(
				organisationId, actorUserId, accessToken);
		Appointment appointment = appointmentRepository
				.findByIdAndOrganisationId(appointmentId, organisationId)
				.orElseThrow(AppointmentNotFoundException::new);
		if (!actorUserId.equals(appointment.getDoctorUserId())
				|| !membership.membershipId().equals(
						appointment.getDoctorMembershipId())) {
			throw new SchedulingAccessDeniedException();
		}
		return new ClinicalAppointmentContextResponse(
				appointment.getId(),
				appointment.getOrganisationId(),
				appointment.getPatientRegistrationId(),
				appointment.getPatientId(),
				appointment.getDoctorUserId(),
				appointment.getDoctorMembershipId(),
				appointment.getStatus(),
				appointment.getStartsAt(),
				appointment.getEndsAt(),
				appointment.getVersion());
	}
}
