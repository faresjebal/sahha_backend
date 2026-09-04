package com.sahha.scheduling.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.service.availabilityservice.AvailabilityRuleValidator;
import com.sahha.scheduling.service.availabilityservice.SlotCalculationService;

class SlotCalculationServiceTests {

	private static final Clock CLOCK = Clock.fixed(
			Instant.parse("2030-01-07T07:00:00Z"),
			ZoneOffset.UTC);

	@Test
	void subtractsBreaksAndTimeOffFromDeterministicSlots() {
		DoctorAvailabilitySchedule schedule = schedule(
				List.of(new DoctorAvailabilitySchedule.BreakValue(
						DayOfWeek.MONDAY,
						LocalTime.of(10, 0),
						LocalTime.of(10, 30),
						"Break")),
				List.of(new DoctorAvailabilitySchedule.TimeOffValue(
						LocalDate.of(2030, 1, 14),
						LocalTime.of(9, 0),
						LocalTime.of(9, 30),
						"Meeting")));

		var slots = new SlotCalculationService(CLOCK).calculate(
				schedule,
				LocalDate.of(2030, 1, 14),
				LocalDate.of(2030, 1, 14));

		assertEquals(
				List.of(LocalTime.of(9, 30), LocalTime.of(10, 30)),
				slots.stream().map(value -> value.localStartTime()).toList());
	}

	@Test
	void rejectsOverlappingWindowsAndOutOfHorizonRanges() {
		AvailabilityRuleValidator validator = new AvailabilityRuleValidator();
		assertThrows(
				IllegalArgumentException.class,
				() -> validator.validate(
						List.of(
								new DoctorAvailabilitySchedule.WeeklyWindowValue(
										DayOfWeek.MONDAY,
										LocalTime.of(9, 0),
										LocalTime.of(11, 0)),
								new DoctorAvailabilitySchedule.WeeklyWindowValue(
										DayOfWeek.MONDAY,
										LocalTime.of(10, 30),
										LocalTime.NOON)),
						List.of(),
						List.of()));

		assertThrows(
				IllegalArgumentException.class,
				() -> new SlotCalculationService(CLOCK).calculate(
						schedule(List.of(), List.of()),
						LocalDate.of(2031, 1, 1),
						LocalDate.of(2031, 1, 1)));
	}

	private static DoctorAvailabilitySchedule schedule(
			List<DoctorAvailabilitySchedule.BreakValue> breaks,
			List<DoctorAvailabilitySchedule.TimeOffValue> timeOff) {
		return DoctorAvailabilitySchedule.create(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"UTC",
				30,
				0,
				60,
				"Synthetic clinic",
				List.of(new DoctorAvailabilitySchedule.WeeklyWindowValue(
						DayOfWeek.MONDAY,
						LocalTime.of(9, 0),
						LocalTime.of(11, 0))),
				breaks,
				timeOff,
				UUID.randomUUID(),
				CLOCK);
	}
}
