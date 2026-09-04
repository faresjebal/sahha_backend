package com.sahha.scheduling.service.availabilityservice;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.client.organisation.SchedulingDoctorDirectoryClient;
import com.sahha.scheduling.client.patient.PatientRegistryClient;
import com.sahha.scheduling.client.patient.PatientSchedulingContextResource;
import com.sahha.scheduling.dto.request.UpsertDoctorAvailabilityRequest;
import com.sahha.scheduling.dto.response.AvailableSlotResponse;
import com.sahha.scheduling.dto.response.AvailableSlotsResponse;
import com.sahha.scheduling.dto.response.DoctorAvailabilityResponse;
import com.sahha.scheduling.dto.response.DoctorAvailabilitySummaryResponse;
import com.sahha.scheduling.dto.response.PatientDoctorAvailabilityResponse;
import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.exception.AvailabilityNotFoundException;
import com.sahha.scheduling.exception.ConcurrentAvailabilityModificationException;
import com.sahha.scheduling.mapper.DoctorAvailabilityMapper;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;

@Service
public class DoctorAvailabilityService {

	private final DoctorAvailabilityScheduleRepository repository;
	private final AppointmentRepository appointmentRepository;
	private final DoctorAvailabilityMapper mapper;
	private final AvailabilityRuleValidator ruleValidator;
	private final SlotCalculationService slotCalculationService;
	private final SchedulingAccessService accessService;
	private final PatientRegistryClient patientRegistryClient;
	private final SchedulingDoctorDirectoryClient doctorDirectoryClient;
	private final Clock clock;

	public DoctorAvailabilityService(
			DoctorAvailabilityScheduleRepository repository,
			AppointmentRepository appointmentRepository,
			DoctorAvailabilityMapper mapper,
			AvailabilityRuleValidator ruleValidator,
			SlotCalculationService slotCalculationService,
			SchedulingAccessService accessService,
			PatientRegistryClient patientRegistryClient,
			SchedulingDoctorDirectoryClient doctorDirectoryClient,
			Clock clock) {
		this.repository = repository;
		this.appointmentRepository = appointmentRepository;
		this.mapper = mapper;
		this.ruleValidator = ruleValidator;
		this.slotCalculationService = slotCalculationService;
		this.accessService = accessService;
		this.patientRegistryClient = patientRegistryClient;
		this.doctorDirectoryClient = doctorDirectoryClient;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<PatientDoctorAvailabilityResponse> listPatientDoctors(
			UUID registrationId,
			UUID actorUserId,
			String accessToken) {
		PatientSchedulingContextResource context = patientRegistryClient
				.findOwnActiveRegistration(registrationId, actorUserId, accessToken);
		return repository.findAllByOrganisationIdOrderByDoctorUserId(
				context.organisationId()).stream()
				.map(schedule -> {
					var doctor = doctorDirectoryClient.findPatientVisibleActiveDoctor(
							context.organisationId(),
							schedule.getDoctorUserId(),
							accessToken);
					return new PatientDoctorAvailabilityResponse(
							schedule.getDoctorUserId(),
							doctor.displayName(),
							schedule.getTimeZone(),
							schedule.getLocationLabel(),
							schedule.getAppointmentDurationMinutes());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public AvailableSlotsResponse patientSlots(
			UUID registrationId,
			UUID doctorUserId,
			LocalDate from,
			LocalDate to,
			UUID actorUserId,
			String accessToken) {
		PatientSchedulingContextResource context = patientRegistryClient
				.findOwnActiveRegistration(registrationId, actorUserId, accessToken);
		doctorDirectoryClient.findPatientVisibleActiveDoctor(
				context.organisationId(), doctorUserId, accessToken);
		return calculateAvailableSlots(
				context.organisationId(), doctorUserId, from, to);
	}

	@Transactional(readOnly = true)
	public DoctorAvailabilityResponse findMine(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		accessService.requireDoctor(organisationId, actorUserId, accessToken);
		return mapper.response(findScoped(organisationId, actorUserId));
	}

	@Transactional
	public DoctorAvailabilityResponse upsertMine(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			UpsertDoctorAvailabilityRequest request) {
		OrganisationContextResource context = accessService.requireDoctor(
				organisationId, actorUserId, accessToken);
		List<DoctorAvailabilitySchedule.WeeklyWindowValue> windows =
				request.weeklyWindows().stream()
						.map(value -> new DoctorAvailabilitySchedule.WeeklyWindowValue(
								value.dayOfWeek(), value.startTime(), value.endTime()))
						.toList();
		List<DoctorAvailabilitySchedule.BreakValue> breaks = request.breaks() == null
				? List.of()
				: request.breaks().stream()
						.map(value -> new DoctorAvailabilitySchedule.BreakValue(
								value.dayOfWeek(), value.startTime(), value.endTime(),
								value.label()))
						.toList();
		List<DoctorAvailabilitySchedule.TimeOffValue> timeOff =
				request.timeOff() == null
						? List.of()
						: request.timeOff().stream()
								.map(value -> new DoctorAvailabilitySchedule.TimeOffValue(
										value.date(), value.startTime(), value.endTime(),
										value.reason()))
								.toList();
		ruleValidator.validate(windows, breaks, timeOff);
		try {
			DoctorAvailabilitySchedule schedule = repository
					.findByOrganisationIdAndDoctorUserId(
							organisationId, actorUserId)
					.orElse(null);
			if (schedule == null) {
				if (request.version() != null && request.version() != 0) {
					throw new ConcurrentAvailabilityModificationException();
				}
				schedule = DoctorAvailabilitySchedule.create(
						organisationId,
						actorUserId,
						context.membershipId(),
						request.timeZone(),
						request.appointmentDurationMinutes(),
						request.minimumLeadTimeMinutes(),
						request.bookingHorizonDays(),
						request.locationLabel(),
						windows,
						breaks,
						timeOff,
						actorUserId,
						clock);
			}
			else {
				if (request.version() == null
						|| request.version() != schedule.getVersion()) {
					throw new ConcurrentAvailabilityModificationException();
				}
				schedule.replace(
						request.timeZone(),
						request.appointmentDurationMinutes(),
						request.minimumLeadTimeMinutes(),
						request.bookingHorizonDays(),
						request.locationLabel(),
						windows,
						breaks,
						timeOff,
						actorUserId,
						clock);
			}
			return mapper.response(repository.saveAndFlush(schedule));
		}
		catch (ObjectOptimisticLockingFailureException
				| DataIntegrityViolationException conflict) {
			throw new ConcurrentAvailabilityModificationException();
		}
	}

	@Transactional(readOnly = true)
	public List<DoctorAvailabilitySummaryResponse> listDoctors(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		OrganisationContextResource context = accessService.requireDirectoryRead(
				organisationId, actorUserId, accessToken);
		return repository.findAllByOrganisationIdOrderByDoctorUserId(organisationId)
				.stream()
				.filter(schedule -> context.roles().contains("RECEPTIONIST")
						|| context.roles().contains("ORGANIZATION_ADMIN")
						|| schedule.getDoctorUserId().equals(actorUserId))
				.map(mapper::summary)
				.toList();
	}

	@Transactional(readOnly = true)
	public AvailableSlotsResponse slots(
			UUID organisationId,
			UUID doctorUserId,
			LocalDate from,
			LocalDate to,
			UUID actorUserId,
			String accessToken) {
		accessService.requireSlotRead(
				organisationId, actorUserId, doctorUserId, accessToken);
		return calculateAvailableSlots(organisationId, doctorUserId, from, to);
	}

	private AvailableSlotsResponse calculateAvailableSlots(
			UUID organisationId,
			UUID doctorUserId,
			LocalDate from,
			LocalDate to) {
		DoctorAvailabilitySchedule schedule = findScoped(organisationId, doctorUserId);
		List<AvailableSlotResponse> calculated =
				slotCalculationService.calculate(schedule, from, to);
		ZoneId zone = ZoneId.of(schedule.getTimeZone());
		Instant rangeStart = from.atStartOfDay(zone).toInstant();
		Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
		var occupied = appointmentRepository.findBlockingDoctorAppointments(
				organisationId,
				doctorUserId,
				AppointmentStatus.blockingStatuses(),
				rangeStart,
				rangeEnd);
		var available = calculated.stream()
				.filter(slot -> occupied.stream().noneMatch(appointment ->
						appointment.getStartsAt().isBefore(slot.endsAt())
								&& appointment.getEndsAt().isAfter(slot.startsAt())))
				.toList();
		return new AvailableSlotsResponse(
				organisationId,
				doctorUserId,
				from,
				to,
				schedule.getTimeZone(),
				schedule.getLocationLabel(),
				schedule.getAppointmentDurationMinutes(),
				available);
	}

	private DoctorAvailabilitySchedule findScoped(
			UUID organisationId,
			UUID doctorUserId) {
		return repository.findByOrganisationIdAndDoctorUserId(
				organisationId, doctorUserId)
				.orElseThrow(AvailabilityNotFoundException::new);
	}
}
