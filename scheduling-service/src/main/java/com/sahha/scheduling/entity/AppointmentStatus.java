package com.sahha.scheduling.entity;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
	REQUESTED,
	CONFIRMED,
	RESCHEDULED,
	CANCELLED,
	CHECKED_IN,
	IN_PROGRESS,
	COMPLETED,
	NO_SHOW,
	REJECTED;

	public boolean blocksDoctorTime() {
		return this != CANCELLED && this != REJECTED;
	}

	public static Set<AppointmentStatus> blockingStatuses() {
		return Set.copyOf(EnumSet.complementOf(EnumSet.of(
				CANCELLED,
				REJECTED)));
	}
}
