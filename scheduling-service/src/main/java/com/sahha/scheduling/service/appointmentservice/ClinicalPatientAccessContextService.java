package com.sahha.scheduling.service.appointmentservice;

import java.util.EnumSet;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.dto.response.ClinicalPatientAccessContextResponse;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.exception.AppointmentNotFoundException;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.service.availabilityservice.SchedulingAccessService;

@Service
public class ClinicalPatientAccessContextService {

	private static final EnumSet<AppointmentStatus> ACTIVE_CARE_STATUSES =
			EnumSet.of(
					AppointmentStatus.CONFIRMED,
					AppointmentStatus.CHECKED_IN,
					AppointmentStatus.IN_PROGRESS);

	private final AppointmentRepository appointmentRepository;
	private final SchedulingAccessService accessService;

	public ClinicalPatientAccessContextService(
			AppointmentRepository appointmentRepository,
			SchedulingAccessService accessService) {
		this.appointmentRepository = appointmentRepository;
		this.accessService = accessService;
	}

	@Transactional(readOnly = true)
	public ClinicalPatientAccessContextResponse resolve(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			UUID patientRegistrationId) {
		OrganisationContextResource membership = accessService.requireDoctor(
				organisationId, actorUserId, accessToken);
		Appointment appointment = appointmentRepository
				.findFirstByOrganisationIdAndPatientRegistrationIdAndDoctorUserIdAndStatusInOrderByStartsAtDesc(
						organisationId,
						patientRegistrationId,
						actorUserId,
						ACTIVE_CARE_STATUSES)
				.orElseThrow(AppointmentNotFoundException::new);
		if (!membership.membershipId().equals(appointment.getDoctorMembershipId())) {
			throw new AppointmentNotFoundException();
		}
		return new ClinicalPatientAccessContextResponse(
				appointment.getId(),
				appointment.getOrganisationId(),
				appointment.getPatientRegistrationId(),
				appointment.getPatientId(),
				appointment.getDoctorUserId(),
				appointment.getDoctorMembershipId(),
				appointment.getStatus(),
				appointment.getVersion());
	}
}
