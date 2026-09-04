package com.sahha.scheduling.entity;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "doctor_availability_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class DoctorAvailabilitySchedule {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "doctor_user_id", nullable = false, updatable = false)
	private UUID doctorUserId;

	@Column(name = "doctor_membership_id", nullable = false, updatable = false)
	private UUID doctorMembershipId;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

	@Column(name = "appointment_duration_minutes", nullable = false)
	private int appointmentDurationMinutes;

	@Column(name = "minimum_lead_time_minutes", nullable = false)
	private int minimumLeadTimeMinutes;

	@Column(name = "booking_horizon_days", nullable = false)
	private int bookingHorizonDays;

	@Column(name = "location_label", nullable = false, length = 160)
	private String locationLabel;

	@OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL,
			orphanRemoval = true)
	@OrderBy("dayOfWeek ASC, startTime ASC")
	private List<DoctorWeeklyAvailability> weeklyWindows = new ArrayList<>();

	@OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL,
			orphanRemoval = true)
	@OrderBy("dayOfWeek ASC, startTime ASC")
	private List<DoctorAvailabilityBreak> breaks = new ArrayList<>();

	@OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL,
			orphanRemoval = true)
	@OrderBy("date ASC, startTime ASC")
	private List<DoctorTimeOff> timeOff = new ArrayList<>();

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static DoctorAvailabilitySchedule create(
			UUID organisationId,
			UUID doctorUserId,
			UUID doctorMembershipId,
			String timeZone,
			int appointmentDurationMinutes,
			int minimumLeadTimeMinutes,
			int bookingHorizonDays,
			String locationLabel,
			Collection<WeeklyWindowValue> windows,
			Collection<BreakValue> breaks,
			Collection<TimeOffValue> timeOff,
			UUID actorUserId,
			Clock clock) {
		DoctorAvailabilitySchedule schedule = new DoctorAvailabilitySchedule();
		schedule.id = UUID.randomUUID();
		schedule.organisationId = Objects.requireNonNull(organisationId);
		schedule.doctorUserId = Objects.requireNonNull(doctorUserId);
		schedule.doctorMembershipId = Objects.requireNonNull(doctorMembershipId);
		schedule.createdBy = Objects.requireNonNull(actorUserId);
		schedule.updatedBy = actorUserId;
		schedule.createdAt = Instant.now(Objects.requireNonNull(clock));
		schedule.updatedAt = schedule.createdAt;
		schedule.replace(
				timeZone,
				appointmentDurationMinutes,
				minimumLeadTimeMinutes,
				bookingHorizonDays,
				locationLabel,
				windows,
				breaks,
				timeOff,
				actorUserId,
				clock);
		return schedule;
	}

	public void replace(
			String timeZone,
			int appointmentDurationMinutes,
			int minimumLeadTimeMinutes,
			int bookingHorizonDays,
			String locationLabel,
			Collection<WeeklyWindowValue> windows,
			Collection<BreakValue> breaks,
			Collection<TimeOffValue> timeOff,
			UUID actorUserId,
			Clock clock) {
		this.timeZone = validZone(timeZone);
		if (appointmentDurationMinutes < 5
				|| appointmentDurationMinutes > 240
				|| appointmentDurationMinutes % 5 != 0) {
			throw new IllegalArgumentException("appointment duration is invalid");
		}
		if (minimumLeadTimeMinutes < 0 || minimumLeadTimeMinutes > 43_200) {
			throw new IllegalArgumentException("minimum lead time is invalid");
		}
		if (bookingHorizonDays < 1 || bookingHorizonDays > 365) {
			throw new IllegalArgumentException("booking horizon is invalid");
		}
		this.appointmentDurationMinutes = appointmentDurationMinutes;
		this.minimumLeadTimeMinutes = minimumLeadTimeMinutes;
		this.bookingHorizonDays = bookingHorizonDays;
		this.locationLabel = required(locationLabel, 160, "locationLabel");
		replaceChildren(windows, breaks, timeOff);
		this.updatedBy = Objects.requireNonNull(actorUserId);
		this.updatedAt = Instant.now(Objects.requireNonNull(clock));
	}

	private void replaceChildren(
			Collection<WeeklyWindowValue> windows,
			Collection<BreakValue> breakValues,
			Collection<TimeOffValue> timeOffValues) {
		if (windows == null || windows.isEmpty()) {
			throw new IllegalArgumentException("at least one weekly window is required");
		}
		weeklyWindows.clear();
		windows.forEach(value -> weeklyWindows.add(
				DoctorWeeklyAvailability.create(this, value)));
		this.breaks.clear();
		if (breakValues != null) {
			breakValues.forEach(value -> this.breaks.add(
					DoctorAvailabilityBreak.create(this, value)));
		}
		this.timeOff.clear();
		if (timeOffValues != null) {
			timeOffValues.forEach(value -> this.timeOff.add(
					DoctorTimeOff.create(this, value)));
		}
	}

	private static String validZone(String value) {
		String zone = required(value, 64, "timeZone");
		ZoneId.of(zone);
		return zone;
	}

	static String required(String value, int maximumLength, String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String stripped = value.strip().replaceAll("\\s+", " ");
		if (stripped.isEmpty() || stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}

	static String optional(String value, int maximumLength, String fieldName) {
		return value == null || value.isBlank()
				? null
				: required(value, maximumLength, fieldName);
	}

	public record WeeklyWindowValue(
			DayOfWeek dayOfWeek,
			LocalTime startTime,
			LocalTime endTime) {
	}

	public record BreakValue(
			DayOfWeek dayOfWeek,
			LocalTime startTime,
			LocalTime endTime,
			String label) {
	}

	public record TimeOffValue(
			LocalDate date,
			LocalTime startTime,
			LocalTime endTime,
			String reason) {
	}
}
