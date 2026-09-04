package com.sahha.communication.controller;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.communication.dto.request.CreateConversationRequest;
import com.sahha.communication.dto.request.SendMessageRequest;
import com.sahha.communication.dto.response.ConversationPageResponse;
import com.sahha.communication.dto.response.ConversationResponse;
import com.sahha.communication.dto.response.MessagePageResponse;
import com.sahha.communication.dto.response.MessageResponse;
import com.sahha.communication.exception.CommunicationAccessDeniedException;
import com.sahha.communication.security.CommunicationAccessTokenValidator;
import com.sahha.communication.service.conversationservice.ConversationService;

@RestController
@RequestMapping("/api/v1/conversations")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Doctor conversations",
		description = "Participant-only doctor conversations. Patient context never grants record access.")
public class ConversationController {
	private final ConversationService service;
	public ConversationController(ConversationService service) { this.service = service; }

	@PostMapping
	@Operation(operationId = "createConversation", summary = "Start a direct doctor conversation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ConversationResponse> create(
			@Valid @RequestBody CreateConversationRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		ConversationResponse created = service.create(organisationId(jwt), userId(jwt),
				jwt.getTokenValue(), request);
		return ResponseEntity.created(URI.create("/api/v1/conversations/" + created.id()))
				.cacheControl(CacheControl.noStore()).body(created);
	}

	@GetMapping
	@Operation(operationId = "listConversations", summary = "List the authenticated doctor's conversations")
	public ResponseEntity<ConversationPageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.list(organisationId(jwt), userId(jwt), jwt.getTokenValue(), page, size));
	}

	@GetMapping("/{conversationId}")
	@Operation(operationId = "getConversation", summary = "Read one participant-scoped conversation")
	public ResponseEntity<ConversationResponse> find(@PathVariable UUID conversationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.find(conversationId, organisationId(jwt), userId(jwt), jwt.getTokenValue()));
	}

	@GetMapping("/{conversationId}/messages")
	@Operation(operationId = "listConversationMessages", summary = "Read immutable conversation messages")
	public ResponseEntity<MessagePageResponse> messages(@PathVariable UUID conversationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.messages(conversationId, organisationId(jwt), userId(jwt),
						jwt.getTokenValue(), page, size));
	}

	@PostMapping("/{conversationId}/messages")
	@Operation(operationId = "sendConversationMessage", summary = "Append an immutable message")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<MessageResponse> send(@PathVariable UUID conversationId,
			@Valid @RequestBody SendMessageRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
				.body(service.send(conversationId, organisationId(jwt), userId(jwt),
						jwt.getTokenValue(), request));
	}

	@PostMapping("/{conversationId}/read")
	@Operation(operationId = "markConversationRead", summary = "Advance the participant read marker")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ConversationResponse> read(@PathVariable UUID conversationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.markRead(conversationId, organisationId(jwt), userId(jwt), jwt.getTokenValue()));
	}

	private static UUID userId(Jwt jwt) {
		try { return UUID.fromString(jwt.getSubject()); }
		catch (RuntimeException invalid) { throw new CommunicationAccessDeniedException(); }
	}
	private static UUID organisationId(Jwt jwt) {
		try { return UUID.fromString(jwt.getClaimAsString(
				CommunicationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM)); }
		catch (RuntimeException invalid) { throw new CommunicationAccessDeniedException(); }
	}
}
