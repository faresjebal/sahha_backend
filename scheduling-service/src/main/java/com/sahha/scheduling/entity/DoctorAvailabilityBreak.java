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
@Table(name = "doctor_availability_break")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoctorAvailabilityBreak {

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

	@Column(length = 80)
	private String label;

	static DoctorAvailabilityBreak create(
			DoctorAvailabilitySchedule schedule,
			DoctorAvailabilitySchedule.BreakValue value) {
		Objects.requireNonNull(value, "break must not be null");
		DoctorWeeklyAvailability.validatePeriod(
				value.startTime(), value.endTime());
		DoctorAvailabilityBreak availabilityBreak =
				new DoctorAvailabilityBreak();
		availabilityBreak.id = UUID.randomUUID();
		availabilityBreak.schedule = Objects.requireNonNull(schedule);
		availabilityBreak.dayOfWeek = Objects.requireNonNull(value.dayOfWeek());
		availabilityBreak.startTime = value.startTime();
		availabilityBreak.endTime = value.endTime();
		availabilityBreak.label = DoctorAvailabilitySchedule.optional(
				value.label(), 80, "label");
		return availabilityBreak;
	}
}
