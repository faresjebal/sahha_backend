package com.sahha.scheduling.service.availabilityservice;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sahha.scheduling.entity.DoctorAvailabilitySchedule.BreakValue;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule.TimeOffValue;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule.WeeklyWindowValue;

@Component
public class AvailabilityRuleValidator {

	public void validate(
			List<WeeklyWindowValue> windows,
			List<BreakValue> breaks,
			List<TimeOffValue> timeOff) {
		validateWindows(windows);
		validateBreaks(windows, breaks);
		validateTimeOff(timeOff);
	}

	private static void validateWindows(List<WeeklyWindowValue> windows) {
		if (windows == null || windows.isEmpty()) {
			throw new IllegalArgumentException(
					"At least one weekly availability window is required.");
		}
		for (var day : java.time.DayOfWeek.values()) {
			List<WeeklyWindowValue> values = windows.stream()
					.filter(value -> value.dayOfWeek() == day)
					.sorted(Comparator.comparing(WeeklyWindowValue::startTime))
					.toList();
			for (int index = 1; index < values.size(); index++) {
				if (overlaps(
						values.get(index - 1).startTime(),
						values.get(index - 1).endTime(),
						values.get(index).startTime(),
						values.get(index).endTime())) {
					throw new IllegalArgumentException(
							"Weekly availability windows cannot overlap.");
				}
			}
		}
	}

	private static void validateBreaks(
			List<WeeklyWindowValue> windows,
			List<BreakValue> breaks) {
		List<BreakValue> safeBreaks = breaks == null ? List.of() : breaks;
		for (BreakValue value : safeBreaks) {
			boolean contained = windows.stream().anyMatch(window ->
					window.dayOfWeek() == value.dayOfWeek()
							&& !value.startTime().isBefore(window.startTime())
							&& !value.endTime().isAfter(window.endTime()));
			if (!contained) {
				throw new IllegalArgumentException(
						"Every break must be inside one availability window.");
			}
		}
		for (var day : java.time.DayOfWeek.values()) {
			List<BreakValue> values = safeBreaks.stream()
					.filter(value -> value.dayOfWeek() == day)
					.sorted(Comparator.comparing(BreakValue::startTime))
					.toList();
			for (int index = 1; index < values.size(); index++) {
				if (overlaps(
						values.get(index - 1).startTime(),
						values.get(index - 1).endTime(),
						values.get(index).startTime(),
						values.get(index).endTime())) {
					throw new IllegalArgumentException("Breaks cannot overlap.");
				}
			}
		}
	}

	private static void validateTimeOff(List<TimeOffValue> timeOff) {
		List<TimeOffValue> values = timeOff == null ? List.of() : timeOff;
		for (int first = 0; first < values.size(); first++) {
			for (int second = first + 1; second < values.size(); second++) {
				TimeOffValue left = values.get(first);
				TimeOffValue right = values.get(second);
				if (!left.date().equals(right.date())) {
					continue;
				}
				if (left.startTime() == null || right.startTime() == null
						|| overlaps(
								left.startTime(), left.endTime(),
								right.startTime(), right.endTime())) {
					throw new IllegalArgumentException(
							"Time-off periods cannot overlap.");
				}
			}
		}
	}

	static boolean overlaps(
			LocalTime firstStart,
			LocalTime firstEnd,
			LocalTime secondStart,
			LocalTime secondEnd) {
		return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
	}
}
