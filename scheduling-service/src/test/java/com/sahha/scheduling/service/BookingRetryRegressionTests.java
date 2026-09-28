package com.sahha.scheduling.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.client.patient.PatientRegistrationResource;
import com.sahha.scheduling.dto.request.BookAppointmentRequest;
import com.sahha.scheduling.mapper.AppointmentMapper;
import com.sahha.scheduling.entity.*;
import com.sahha.scheduling.exception.BookingRequestConflictException;
import com.sahha.scheduling.repository.*;
import com.sahha.scheduling.service.appointmentservice.*;
import com.sahha.scheduling.service.availabilityservice.SlotCalculationService;
import com.sahha.scheduling.outbox.AppointmentOutboxRecorder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingRetryRegressionTests {
    final UUID organisation=UUID.randomUUID(), requestId=UUID.randomUUID(), registration=UUID.randomUUID(),
            patientId=UUID.randomUUID(), doctor=UUID.randomUUID(), doctorMembership=UUID.randomUUID(),
            actor=UUID.randomUUID(), actorMembership=UUID.randomUUID(), schedule=UUID.randomUUID();
    final Clock clock=Clock.fixed(Instant.parse("2026-09-27T08:00:00Z"),ZoneOffset.UTC);
    final Instant original=Instant.parse("2026-10-01T09:00:00Z"), rescheduled=original.plusSeconds(3600);
    final AppointmentRepository appointments=mock(AppointmentRepository.class);
    final AppointmentAuditEventRepository audits=mock(AppointmentAuditEventRepository.class);
    final DoctorAvailabilityScheduleRepository schedules=mock(DoctorAvailabilityScheduleRepository.class);
    final SlotCalculationService slots=mock(SlotCalculationService.class);
    final AppointmentOutboxRecorder outbox=mock(AppointmentOutboxRecorder.class);
    final AppointmentMapper mapper=mock(AppointmentMapper.class);
    final AppointmentBookingPersistenceService service=new AppointmentBookingPersistenceService(
            appointments,audits,schedules,slots,outbox,mapper,clock);
    Appointment appointment;
    @BeforeEach void setup() { fixture(AppointmentActorType.STAFF); }
    void fixture(AppointmentActorType type) {
        var membership=type==AppointmentActorType.STAFF?actorMembership:null;
        appointment=Appointment.request(organisation,requestId,registration,patientId,doctor,doctorMembership,schedule,
                original,original.plusSeconds(1800),"UTC","Synthetic retry fixture",actor,type,membership,clock);
        var booked=AppointmentAuditEvent.booked(appointment,actor,type,membership,"synthetic-original-booking");
        when(appointments.findByOrganisationIdAndBookingRequestId(organisation,requestId)).thenReturn(Optional.of(appointment));
        when(audits.findByOrganisationIdAndCommandRequestId(organisation,requestId)).thenReturn(Optional.of(booked));
        appointment.reschedule(schedule,rescheduled,rescheduled.plusSeconds(1800),"UTC","Synthetic retry fixture","Synthetic reschedule",clock);
    }
    AppointmentBookingResult replay(UUID caller,UUID patient,UUID treatingDoctor,Instant starts,AppointmentActorType type) {
        return service.book(organisation,caller,type,type==AppointmentActorType.STAFF?actorMembership:null,
                new PatientRegistrationResource(patient,patientId,organisation,"ACTIVE"),
                new SchedulingDoctorResource(doctorMembership,organisation,treatingDoctor,"Synthetic doctor",1),
                "synthetic-retry",new BookAppointmentRequest(requestId,patient,treatingDoctor,starts));
    }
    @Test void originalStaffBookingRetriesAfterRescheduleWithoutChangingTheAppointment() {
        assertFalse(replay(actor,registration,doctor,original,AppointmentActorType.STAFF).created());
        assertEquals(rescheduled,appointment.getStartsAt());
        verify(mapper).response(appointment);verifyNoInteractions(schedules,slots,outbox);
        verify(appointments,never()).saveAndFlush(any());verify(audits,never()).saveAndFlush(any());
    }
    @Test void originalPatientBookingAlsoRetriesAfterReschedule() {
        fixture(AppointmentActorType.PATIENT);
        assertFalse(replay(actor,registration,doctor,original,AppointmentActorType.PATIENT).created());
        assertEquals(rescheduled,appointment.getStartsAt());verifyNoInteractions(schedules,slots,outbox);
    }
    @Test void substitutingTheCurrentTimeIsStillAConflictingOriginalCommand() {
        assertThrows(BookingRequestConflictException.class,
                ()->replay(actor,registration,doctor,rescheduled,AppointmentActorType.STAFF));
    }
    @Test void changingActorPatientOrDoctorCannotAdoptTheOriginalCommand() {
        assertThrows(BookingRequestConflictException.class,()->replay(UUID.randomUUID(),registration,doctor,original,AppointmentActorType.STAFF));
        assertThrows(BookingRequestConflictException.class,()->replay(actor,UUID.randomUUID(),doctor,original,AppointmentActorType.STAFF));
        assertThrows(BookingRequestConflictException.class,()->replay(actor,registration,UUID.randomUUID(),original,AppointmentActorType.STAFF));
    }
    @Test void missingOriginalSnapshotCannotFallBackToMutableAppointmentTime() {
        when(audits.findByOrganisationIdAndCommandRequestId(organisation,requestId)).thenReturn(Optional.empty());
        assertThrows(BookingRequestConflictException.class,()->replay(actor,registration,doctor,rescheduled,AppointmentActorType.STAFF));
    }
}
