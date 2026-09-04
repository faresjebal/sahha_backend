package com.sahha.scheduling.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "doctor_weekly_availability")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoctorWeeklyAvailability {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "schedule_id", nullable = false, updatable = false)
	private DoctorAvailabilitySchedule schedule;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 9)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	static DoctorWeeklyAvailability create(
			DoctorAvailabilitySchedule schedule,
			DoctorAvailabilitySchedule.WeeklyWindowValue value) {
		Objects.requireNonNull(value, "weekly window must not be null");
		validatePeriod(value.startTime(), value.endTime());
		DoctorWeeklyAvailability window = new DoctorWeeklyAvailability();
		window.id = UUID.randomUUID();
		window.schedule = Objects.requireNonNull(schedule);
		window.dayOfWeek = Objects.requireNonNull(value.dayOfWeek());
		window.startTime = value.startTime();
		window.endTime = value.endTime();
		return window;
	}

	static void validatePeriod(LocalTime startTime, LocalTime endTime) {
		Objects.requireNonNull(startTime, "startTime must not be null");
		Objects.requireNonNull(endTime, "endTime must not be null");
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("endTime must be after startTime");
		}
	}
}
