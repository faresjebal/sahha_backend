package com.sahha.scheduling.service.appointmentservice;

import com.sahha.scheduling.dto.response.AppointmentResponse;

public record AppointmentBookingResult(
		AppointmentResponse appointment,
		boolean created) {
}
