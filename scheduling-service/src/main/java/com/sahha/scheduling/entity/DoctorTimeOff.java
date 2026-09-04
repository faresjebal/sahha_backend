package com.sahha.scheduling.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "doctor_time_off")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoctorTimeOff {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "schedule_id", nullable = false, updatable = false)
	private DoctorAvailabilitySchedule schedule;

	@Column(name = "time_off_date", nullable = false)
	private LocalDate date;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(nullable = false, length = 160)
	private String reason;

	static DoctorTimeOff create(
			DoctorAvailabilitySchedule schedule,
			DoctorAvailabilitySchedule.TimeOffValue value) {
		Objects.requireNonNull(value, "time off must not be null");
		if ((value.startTime() == null) != (value.endTime() == null)) {
			throw new IllegalArgumentException(
					"time off start and end must both be present or absent");
		}
		if (value.startTime() != null) {
			DoctorWeeklyAvailability.validatePeriod(
					value.startTime(), value.endTime());
		}
		DoctorTimeOff timeOff = new DoctorTimeOff();
		timeOff.id = UUID.randomUUID();
		timeOff.schedule = Objects.requireNonNull(schedule);
		timeOff.date = Objects.requireNonNull(value.date());
		timeOff.startTime = value.startTime();
		timeOff.endTime = value.endTime();
		timeOff.reason = DoctorAvailabilitySchedule.required(
				value.reason(), 160, "reason");
		return timeOff;
	}
}
