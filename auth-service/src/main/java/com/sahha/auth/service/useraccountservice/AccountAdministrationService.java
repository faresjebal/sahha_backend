package com.sahha.auth.service.useraccountservice;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.dto.request.AccountAdministrationAction;
import com.sahha.auth.entity.PlatformRoleCode;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.exception.ForbiddenAccountOperationException;
import com.sahha.auth.exception.OwnedSessionNotFoundException;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.UserPlatformRoleRepository;
import com.sahha.auth.service.usersessionservice.UserSessionService;
import com.sahha.auth.service.securityeventservice.SecurityEventContext;
import com.sahha.auth.service.securityeventservice.SecurityEventRecorder;

@Service
public class AccountAdministrationService {

	private final UserAccountRepository userRepository;
	private final UserPlatformRoleRepository roleRepository;
	private final UserSessionService sessionService;
	private final SecurityEventRecorder securityEventRecorder;

	public AccountAdministrationService(
			UserAccountRepository userRepository,
			UserPlatformRoleRepository roleRepository,
			UserSessionService sessionService,
			SecurityEventRecorder securityEventRecorder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.sessionService = sessionService;
		this.securityEventRecorder = securityEventRecorder;
	}

	@Transactional
	public void updateStatus(
			UUID actorUserId,
			UUID targetUserId,
			AccountAdministrationAction action,
			Instant changedAt) {
		Objects.requireNonNull(actorUserId, "actorUserId must not be null");
		Objects.requireNonNull(targetUserId, "targetUserId must not be null");
		AccountAdministrationAction requiredAction = Objects.requireNonNull(
				action,
				"action must not be null");
		Instant requiredChangedAt = Objects.requireNonNull(
				changedAt,
				"changedAt must not be null");
		if (actorUserId.equals(targetUserId)
				|| !roleRepository
						.existsByUser_IdAndRole_CodeAndActiveTrue(
								actorUserId,
								PlatformRoleCode.PLATFORM_ADMIN)) {
			throw new ForbiddenAccountOperationException();
		}

		UserAccount target = userRepository.findByIdForUpdate(targetUserId)
				.orElseThrow(OwnedSessionNotFoundException::new);
		switch (requiredAction) {
			case SUSPEND -> target.suspend();
			case REACTIVATE -> target.reactivate();
			case DISABLE -> target.disable();
		}
		userRepository.saveAndFlush(target);
		if (requiredAction != AccountAdministrationAction.REACTIVATE) {
			sessionService.revokeAllForSecurityChange(
					targetUserId,
					requiredChangedAt,
					"ACCOUNT_" + requiredAction.name());
		}
		securityEventRecorder.record(
				eventType(requiredAction),
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.administration(
						actorUserId,
						targetUserId),
				requiredChangedAt);
	}

	private static SecurityEventType eventType(
			AccountAdministrationAction action) {
		return switch (action) {
			case SUSPEND -> SecurityEventType.ACCOUNT_SUSPENDED;
			case REACTIVATE -> SecurityEventType.ACCOUNT_REACTIVATED;
			case DISABLE -> SecurityEventType.ACCOUNT_DISABLED;
		};
	}
}
