package com.sahha.scheduling.service.availabilityservice;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sahha.scheduling.dto.response.AvailableSlotResponse;
import com.sahha.scheduling.entity.DoctorAvailabilityBreak;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;

@Component
public class SlotCalculationService {

	private final Clock clock;

	public SlotCalculationService(Clock clock) {
		this.clock = clock;
	}

	public List<AvailableSlotResponse> calculate(
			DoctorAvailabilitySchedule schedule,
			LocalDate from,
			LocalDate to) {
		ZoneId zone = ZoneId.of(schedule.getTimeZone());
		validateRange(schedule, from, to, zone);
		Duration duration = Duration.ofMinutes(
				schedule.getAppointmentDurationMinutes());
		Instant earliest = clock.instant().plus(Duration.ofMinutes(
				schedule.getMinimumLeadTimeMinutes()));
		List<AvailableSlotResponse> slots = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			LocalDate currentDate = date;
			schedule.getWeeklyWindows().stream()
					.filter(window -> window.getDayOfWeek() == currentDate.getDayOfWeek())
					.sorted(Comparator.comparing(value -> value.getStartTime()))
					.forEach(window -> addWindowSlots(
							schedule, currentDate, window.getStartTime(),
							window.getEndTime(), zone, duration, earliest, slots));
		}
		return List.copyOf(slots);
	}

	private void validateRange(
			DoctorAvailabilitySchedule schedule,
			LocalDate from,
			LocalDate to,
			ZoneId zone) {
		if (from == null || to == null || to.isBefore(from)
				|| Duration.between(
						from.atStartOfDay(), to.plusDays(1).atStartOfDay())
						.toDays() > 31) {
			throw new IllegalArgumentException(
					"Slot ranges must contain between one and 31 days.");
		}
		LocalDate today = LocalDate.now(clock.withZone(zone));
		if (from.isBefore(today)
				|| to.isAfter(today.plusDays(schedule.getBookingHorizonDays()))) {
			throw new IllegalArgumentException(
					"Slot range is outside the booking horizon.");
		}
	}

	private static void addWindowSlots(
			DoctorAvailabilitySchedule schedule,
			LocalDate date,
			LocalTime windowStart,
			LocalTime windowEnd,
			ZoneId zone,
			Duration duration,
			Instant earliest,
			List<AvailableSlotResponse> slots) {
		for (LocalTime start = windowStart;
				!start.plus(duration).isAfter(windowEnd);
				start = start.plus(duration)) {
			LocalTime end = start.plus(duration);
			Instant startsAt = LocalDateTime.of(date, start).atZone(zone).toInstant();
			if (startsAt.isBefore(earliest)
					|| blocked(schedule, date, start, end)) {
				continue;
			}
			slots.add(new AvailableSlotResponse(
					startsAt,
					LocalDateTime.of(date, end).atZone(zone).toInstant(),
					date,
					start,
					end));
		}
	}

	private static boolean blocked(
			DoctorAvailabilitySchedule schedule,
			LocalDate date,
			LocalTime start,
			LocalTime end) {
		boolean breakConflict = schedule.getBreaks().stream()
				.filter(value -> value.getDayOfWeek() == date.getDayOfWeek())
				.anyMatch(value -> overlaps(value, start, end));
		if (breakConflict) {
			return true;
		}
		return schedule.getTimeOff().stream()
				.filter(value -> value.getDate().equals(date))
				.anyMatch(value -> value.getStartTime() == null
						|| AvailabilityRuleValidator.overlaps(
								value.getStartTime(), value.getEndTime(), start, end));
	}

	private static boolean overlaps(
			DoctorAvailabilityBreak value,
			LocalTime start,
			LocalTime end) {
		return AvailabilityRuleValidator.overlaps(
				value.getStartTime(), value.getEndTime(), start, end);
	}
}
