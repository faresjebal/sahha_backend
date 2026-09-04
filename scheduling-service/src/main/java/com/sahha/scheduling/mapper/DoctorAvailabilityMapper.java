package com.sahha.scheduling.mapper;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.sahha.scheduling.dto.response.AvailabilityBreakResponse;
import com.sahha.scheduling.dto.response.DoctorAvailabilityResponse;
import com.sahha.scheduling.dto.response.DoctorAvailabilitySummaryResponse;
import com.sahha.scheduling.dto.response.TimeOffResponse;
import com.sahha.scheduling.dto.response.WeeklyAvailabilityResponse;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;

@Component
public class DoctorAvailabilityMapper {

	public DoctorAvailabilityResponse response(
			DoctorAvailabilitySchedule schedule) {
		return new DoctorAvailabilityResponse(
				schedule.getId(),
				schedule.getOrganisationId(),
				schedule.getDoctorUserId(),
				schedule.getDoctorMembershipId(),
				schedule.getTimeZone(),
				schedule.getAppointmentDurationMinutes(),
				schedule.getMinimumLeadTimeMinutes(),
				schedule.getBookingHorizonDays(),
				schedule.getLocationLabel(),
				schedule.getWeeklyWindows().stream()
						.map(window -> new WeeklyAvailabilityResponse(
								window.getId(),
								window.getDayOfWeek(),
								window.getStartTime(),
								window.getEndTime()))
						.sorted(Comparator
								.comparing((WeeklyAvailabilityResponse value) ->
										value.dayOfWeek().getValue())
								.thenComparing(WeeklyAvailabilityResponse::startTime))
						.toList(),
				schedule.getBreaks().stream()
						.map(value -> new AvailabilityBreakResponse(
								value.getId(),
								value.getDayOfWeek(),
								value.getStartTime(),
								value.getEndTime(),
								value.getLabel()))
						.sorted(Comparator
								.comparing((AvailabilityBreakResponse value) ->
										value.dayOfWeek().getValue())
								.thenComparing(AvailabilityBreakResponse::startTime))
						.toList(),
				schedule.getTimeOff().stream()
						.map(value -> new TimeOffResponse(
								value.getId(),
								value.getDate(),
								value.getStartTime(),
								value.getEndTime(),
								value.getReason()))
						.sorted(Comparator
								.comparing(TimeOffResponse::date)
								.thenComparing(
										TimeOffResponse::startTime,
										Comparator.nullsFirst(Comparator.naturalOrder())))
						.toList(),
				schedule.getCreatedAt(),
				schedule.getUpdatedAt(),
				schedule.getVersion());
	}

	public DoctorAvailabilitySummaryResponse summary(
			DoctorAvailabilitySchedule schedule) {
		return new DoctorAvailabilitySummaryResponse(
				schedule.getDoctorUserId(),
				schedule.getDoctorMembershipId(),
				schedule.getTimeZone(),
				schedule.getLocationLabel(),
				schedule.getAppointmentDurationMinutes());
	}
}
