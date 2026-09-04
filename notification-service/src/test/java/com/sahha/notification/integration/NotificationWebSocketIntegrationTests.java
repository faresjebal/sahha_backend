package com.sahha.notification.integration;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.sahha.notification.client.organisation.OrganisationContextClient;
import com.sahha.notification.client.organisation.OrganisationContextResource;
import com.sahha.notification.config.NotificationWebSocketConfiguration;
import com.sahha.notification.dto.response.RealtimeNotificationMessage;
import com.sahha.notification.event.AppointmentEventSource;
import com.sahha.notification.event.AppointmentEventType;
import com.sahha.notification.event.AppointmentEventV1;
import com.sahha.notification.event.AppointmentStatus;
import com.sahha.notification.repository.AppointmentNotificationCursorRepository;
import com.sahha.notification.repository.ConsumedAppointmentEventRepository;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.security.NotificationPrincipalName;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentEventProcessingResult;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentNotificationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationWebSocketIntegrationTests {

	private static final String FIRST_TOKEN = "realtime.first.token";
	private static final String SECOND_TOKEN = "realtime.second.token";
	private static final String CSRF_TOKEN = "realtime-csrf-token";

	@LocalServerPort
	private int serverPort;

	@Autowired
	private AppointmentNotificationService appointmentNotificationService;

	@Autowired
	private InAppNotificationRepository notificationRepository;

	@Autowired
	private AppointmentNotificationCursorRepository cursorRepository;

	@Autowired
	private ConsumedAppointmentEventRepository consumedRepository;

	@Autowired
	private SimpUserRegistry simpUserRegistry;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	private ThreadPoolTaskScheduler taskScheduler;
	private WebSocketStompClient stompClient;
	private StompSession firstSession;
	private StompSession secondSession;

	@BeforeEach
	void configureClient() {
		taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setPoolSize(1);
		taskScheduler.setThreadNamePrefix("notification-ws-test-");
		taskScheduler.initialize();
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		stompClient.setTaskScheduler(taskScheduler);
		stompClient.start();
	}

	@AfterEach
	void stopClient() {
		disconnect(firstSession);
		disconnect(secondSession);
		if (stompClient != null) {
			stompClient.stop();
		}
		if (taskScheduler != null) {
			taskScheduler.destroy();
		}
	}

	@Test
	void committedNotificationReachesOnlyTheMatchingUserAndOrganisation()
			throws Exception {
		UUID userId = UUID.randomUUID();
		UUID firstOrganisationId = UUID.randomUUID();
		UUID secondOrganisationId = UUID.randomUUID();
		workspace(FIRST_TOKEN, userId, firstOrganisationId);
		workspace(SECOND_TOKEN, userId, secondOrganisationId);

		BlockingQueue<RealtimeNotificationMessage> firstMessages =
				new LinkedBlockingQueue<>();
		BlockingQueue<RealtimeNotificationMessage> secondMessages =
				new LinkedBlockingQueue<>();
		firstSession = connectAndSubscribe(
				FIRST_TOKEN,
				NotificationPrincipalName.of(userId, firstOrganisationId),
				firstMessages);
		secondSession = connectAndSubscribe(
				SECOND_TOKEN,
				NotificationPrincipalName.of(userId, secondOrganisationId),
				secondMessages);

		UUID eventId = UUID.randomUUID();
		UUID appointmentId = UUID.randomUUID();
		try {
			AppointmentEventProcessingResult result =
					appointmentNotificationService.consume(
							event(
									eventId,
									appointmentId,
									firstOrganisationId,
									userId),
							new AppointmentEventSource(
									"realtime-test-" + eventId,
									0,
									0));
			assertEquals(
					AppointmentEventProcessingResult.NOTIFICATION_CREATED,
					result);

			RealtimeNotificationMessage received =
					firstMessages.poll(5, TimeUnit.SECONDS);
			assertNotNull(received);
			assertEquals(
					RealtimeNotificationMessage.NOTIFICATION_CREATED,
					received.messageType());
			assertEquals(appointmentId, received.notification().resourceId());
			assertNull(secondMessages.poll(750, TimeUnit.MILLISECONDS));
		}
		finally {
			notificationRepository.findAll().stream()
					.filter(notification -> eventId.equals(
							notification.getSourceEventId()))
					.forEach(notificationRepository::delete);
			cursorRepository.deleteById(appointmentId);
			consumedRepository.deleteById(eventId);
		}
	}

	@Test
	void stompConnectWithoutTheCookieBoundCsrfHeaderIsRejected() {
		UUID userId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		workspace(FIRST_TOKEN, userId, organisationId);

		assertThrows(
				ExecutionException.class,
				() -> connect(FIRST_TOKEN, false).get(5, TimeUnit.SECONDS));
	}

	private StompSession connectAndSubscribe(
			String token,
			String principalName,
			BlockingQueue<RealtimeNotificationMessage> messages)
			throws Exception {
		StompSession session = connect(token, true).get(5, TimeUnit.SECONDS);
		session.subscribe(
				NotificationWebSocketConfiguration.USER_DESTINATION,
				new RealtimeFrameHandler(messages));
		awaitRegisteredSubscription(principalName);
		return session;
	}

	private void awaitRegisteredSubscription(String principalName)
			throws InterruptedException, TimeoutException {
		Instant deadline = Instant.now().plusSeconds(5);
		do {
			SimpUser user = simpUserRegistry.getUser(principalName);
			if (user != null && user.getSessions().stream()
					.flatMap(session -> session.getSubscriptions().stream())
					.anyMatch(subscription ->
							NotificationWebSocketConfiguration.USER_DESTINATION
									.equals(subscription.getDestination()))) {
				return;
			}
			Thread.sleep(10);
		}
		while (Instant.now().isBefore(deadline));
		throw new TimeoutException(
				"STOMP subscription was not registered for the expected principal");
	}

	private CompletableFuture<StompSession> connect(
			String token,
			boolean includeCsrfHeader) {
		WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
		handshakeHeaders.add(HttpHeaders.ORIGIN, "http://localhost:5173");
		handshakeHeaders.add(
				HttpHeaders.COOKIE,
				"SAHHA_ACCESS_TOKEN=" + token
						+ "; XSRF-TOKEN=" + CSRF_TOKEN);
		StompHeaders connectHeaders = new StompHeaders();
		if (includeCsrfHeader) {
			connectHeaders.add("X-XSRF-TOKEN", CSRF_TOKEN);
		}
		return stompClient.connectAsync(
				URI.create("ws://127.0.0.1:" + serverPort
						+ NotificationWebSocketConfiguration.ENDPOINT),
				handshakeHeaders,
				connectHeaders,
				new StompSessionHandlerAdapter() {
				});
	}

	private void workspace(
			String token,
			UUID userId,
			UUID organisationId) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token, userId, organisationId));
		when(organisationContextClient.resolve(organisationId, token))
				.thenReturn(new OrganisationContextResource(
						UUID.randomUUID(),
						organisationId,
						"Synthetic clinic",
						"CLINIC",
						Set.of("DOCTOR"),
						0));
	}

	private static Jwt jwt(
			String token,
			UUID userId,
			UUID organisationId) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(token)
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_id", organisationId.toString())
				.claim("org_roles", List.of("DOCTOR"))
				.claim("token_type", "access")
				.build();
	}

	private static AppointmentEventV1 event(
			UUID eventId,
			UUID appointmentId,
			UUID organisationId,
			UUID doctorUserId) {
		Instant startsAt = Instant.now().plus(Duration.ofHours(1));
		return new AppointmentEventV1(
				eventId,
				AppointmentEventType.APPOINTMENT_REQUESTED,
				1,
				Instant.now(),
				appointmentId,
				organisationId,
				UUID.randomUUID(),
				doctorUserId,
				UUID.randomUUID(),
				"notification-realtime-test",
				AppointmentStatus.REQUESTED,
				startsAt,
				startsAt.plus(Duration.ofMinutes(30)),
				"Africa/Tunis",
				"Synthetic clinic room",
				0L,
				null,
				null);
	}

	private static void disconnect(StompSession session) {
		if (session != null && session.isConnected()) {
			session.disconnect();
		}
	}

	private static final class RealtimeFrameHandler
			implements StompFrameHandler {

		private final BlockingQueue<RealtimeNotificationMessage> messages;

		private RealtimeFrameHandler(
				BlockingQueue<RealtimeNotificationMessage> messages) {
			this.messages = messages;
		}

		@Override
		public Type getPayloadType(StompHeaders headers) {
			return RealtimeNotificationMessage.class;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			messages.add((RealtimeNotificationMessage) payload);
		}
	}
}
