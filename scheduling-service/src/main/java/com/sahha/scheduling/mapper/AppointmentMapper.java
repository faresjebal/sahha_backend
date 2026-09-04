package com.sahha.scheduling.mapper;

import org.springframework.stereotype.Component;

import com.sahha.scheduling.dto.response.AppointmentResponse;
import com.sahha.scheduling.dto.response.PatientAppointmentResponse;
import com.sahha.scheduling.entity.Appointment;

@Component
public class AppointmentMapper {

	public AppointmentResponse response(Appointment appointment) {
		return new AppointmentResponse(
				appointment.getId(),
				appointment.getOrganisationId(),
				appointment.getBookingRequestId(),
				appointment.getPatientRegistrationId(),
				appointment.getPatientId(),
				appointment.getDoctorUserId(),
				appointment.getDoctorMembershipId(),
				appointment.getStatus(),
				appointment.getStatusReason(),
				appointment.getStartsAt(),
				appointment.getEndsAt(),
				appointment.getTimeZone(),
				appointment.getLocationLabel(),
				appointment.getBookedByUserId(),
				appointment.getBookedByActorType(),
				appointment.getBookedByMembershipId(),
				appointment.getBookedAt(),
				appointment.getCreatedAt(),
				appointment.getUpdatedAt(),
				appointment.getVersion());
	}

	public PatientAppointmentResponse patientResponse(Appointment appointment) {
		return new PatientAppointmentResponse(
				appointment.getId(),
				appointment.getOrganisationId(),
				appointment.getDoctorUserId(),
				appointment.getStatus(),
				appointment.getStatusReason(),
				appointment.getStartsAt(),
				appointment.getEndsAt(),
				appointment.getTimeZone(),
				appointment.getLocationLabel(),
				appointment.getBookedAt(),
				appointment.getUpdatedAt(),
				appointment.getVersion());
	}
}
