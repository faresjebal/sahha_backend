package com.sahha.scheduling.service.appointmentservice;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.client.patient.PatientRegistrationResource;
import com.sahha.scheduling.dto.request.BookAppointmentRequest;
import com.sahha.scheduling.dto.response.AvailableSlotResponse;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentActorType;
import com.sahha.scheduling.entity.AppointmentAuditEvent;
import com.sahha.scheduling.entity.AppointmentAuditEventType;
import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.exception.AppointmentDoctorNotFoundException;
import com.sahha.scheduling.exception.AppointmentSlotUnavailableException;
import com.sahha.scheduling.exception.AvailabilityNotFoundException;
import com.sahha.scheduling.exception.BookingRequestConflictException;
import com.sahha.scheduling.mapper.AppointmentMapper;
import com.sahha.scheduling.repository.AppointmentAuditEventRepository;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;
import com.sahha.scheduling.service.availabilityservice.SlotCalculationService;
import com.sahha.scheduling.outbox.AppointmentOutboxRecorder;

@Service
public class AppointmentBookingPersistenceService {

	private final AppointmentRepository appointmentRepository;
	private final AppointmentAuditEventRepository auditRepository;
	private final DoctorAvailabilityScheduleRepository availabilityRepository;
	private final SlotCalculationService slotCalculationService;
	private final AppointmentOutboxRecorder outboxRecorder;
	private final AppointmentMapper mapper;
	private final Clock clock;

	public AppointmentBookingPersistenceService(
			AppointmentRepository appointmentRepository,
			AppointmentAuditEventRepository auditRepository,
			DoctorAvailabilityScheduleRepository availabilityRepository,
			SlotCalculationService slotCalculationService,
			AppointmentOutboxRecorder outboxRecorder,
			AppointmentMapper mapper,
			Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.auditRepository = auditRepository;
		this.availabilityRepository = availabilityRepository;
		this.slotCalculationService = slotCalculationService;
		this.outboxRecorder = outboxRecorder;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional
	public AppointmentBookingResult book(
			UUID organisationId,
			UUID actorUserId,
			AppointmentActorType actorType,
			UUID actorMembershipId,
			PatientRegistrationResource patient,
			SchedulingDoctorResource doctor,
			String requestId,
			BookAppointmentRequest request) {
		Appointment existing = appointmentRepository
				.findByOrganisationIdAndBookingRequestId(
						organisationId, request.bookingRequestId())
				.orElse(null);
		if (existing != null) {
            // Rescheduling changes the appointment, never the original command.
            // This append-only snapshot was committed atomically with its booking.
            AppointmentAuditEvent original = auditRepository
                    .findByOrganisationIdAndCommandRequestId(organisationId, request.bookingRequestId())
                    .filter(event -> event.getEventType() == AppointmentAuditEventType.APPOINTMENT_BOOKED
                            && existing.getId().equals(event.getAppointmentId())
                            && organisationId.equals(event.getOrganisationId())
                            && actorUserId.equals(event.getActorUserId())
                            && actorType == event.getActorType())
                    .orElseThrow(BookingRequestConflictException::new);
			if (!existing.matchesInitialRequest(
					actorUserId,
					request.patientRegistrationId(),
					request.doctorUserId(),
					request.startsAt(), original.getNewStartsAt())) {
				throw new BookingRequestConflictException();
			}
			return new AppointmentBookingResult(mapper.response(existing), false);
		}

		DoctorAvailabilitySchedule schedule = availabilityRepository
				.findByOrganisationIdAndDoctorUserId(
						organisationId, request.doctorUserId())
				.orElseThrow(AvailabilityNotFoundException::new);
		if (!schedule.getDoctorMembershipId().equals(doctor.membershipId())) {
			throw new AppointmentDoctorNotFoundException();
		}
		ZoneId zone = ZoneId.of(schedule.getTimeZone());
		LocalDate localDate = request.startsAt().atZone(zone).toLocalDate();
		AvailableSlotResponse selectedSlot = slotCalculationService.calculate(
				schedule, localDate, localDate)
				.stream()
				.filter(slot -> slot.startsAt().equals(request.startsAt()))
				.findFirst()
				.orElseThrow(AppointmentSlotUnavailableException::new);
		boolean occupied = !appointmentRepository.findBlockingDoctorAppointments(
				organisationId,
				request.doctorUserId(),
				AppointmentStatus.blockingStatuses(),
				selectedSlot.startsAt(),
				selectedSlot.endsAt()).isEmpty();
		if (occupied) {
			throw new AppointmentSlotUnavailableException();
		}
		Appointment appointment = Appointment.request(
				organisationId,
				request.bookingRequestId(),
				patient.registrationId(),
				patient.patientId(),
				doctor.userId(),
				doctor.membershipId(),
				schedule.getId(),
				selectedSlot.startsAt(),
				selectedSlot.endsAt(),
				schedule.getTimeZone(),
				schedule.getLocationLabel(),
				actorUserId,
				actorType,
				actorMembershipId,
				clock);
		try {
			appointmentRepository.saveAndFlush(appointment);
			AppointmentAuditEvent auditEvent = auditRepository.saveAndFlush(
					AppointmentAuditEvent.booked(
					appointment,
					actorUserId,
					actorType,
					actorMembershipId,
					requestId));
			outboxRecorder.record(appointment, auditEvent);
			return new AppointmentBookingResult(
					mapper.response(appointment), true);
		}
		catch (DataIntegrityViolationException conflict) {
			throw new AppointmentSlotUnavailableException();
		}
	}
}
