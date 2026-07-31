package com.sahha.auth.service.emailservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.sahha.auth.entity.VerificationTokenPurpose;
import com.sahha.auth.service.useraccountservice.AccountTokenDelivery;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;

@Service
public class AuthEmailDispatchService {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(AuthEmailDispatchService.class);

	private final EmailDeliveryService emailDeliveryService;

	public AuthEmailDispatchService(EmailDeliveryService emailDeliveryService) {
		this.emailDeliveryService = emailDeliveryService;
	}

	@Async("authEmailExecutor")
	public void dispatch(AccountTokenDelivery delivery) {
		try {
			IssuedVerificationToken token = delivery.getIssuedToken();
			if (token.getPurpose()
					== VerificationTokenPurpose.EMAIL_VERIFICATION) {
				emailDeliveryService.sendEmailVerification(
						delivery.getRecipientEmail(),
						delivery.getRecipientFirstName(),
						token.getRawToken(),
						token.getExpiresAt());
			}
			else if (token.getPurpose()
					== VerificationTokenPurpose.PASSWORD_RESET) {
				emailDeliveryService.sendPasswordReset(
						delivery.getRecipientEmail(),
						delivery.getRecipientFirstName(),
						token.getRawToken(),
						token.getExpiresAt());
			}
			else {
				throw new IllegalArgumentException(
						"unsupported authentication email purpose");
			}
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"Authentication email delivery failed with {}",
					exception.getClass().getSimpleName());
		}
	}
}
