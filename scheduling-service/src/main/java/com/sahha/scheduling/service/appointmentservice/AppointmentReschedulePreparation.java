package com.sahha.scheduling.service.appointmentservice;

import java.util.UUID;

import com.sahha.scheduling.dto.response.AppointmentResponse;

public record AppointmentReschedulePreparation(
		UUID doctorUserId,
		AppointmentResponse replay) {
}
