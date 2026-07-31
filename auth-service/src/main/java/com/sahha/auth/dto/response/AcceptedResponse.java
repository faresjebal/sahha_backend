package com.sahha.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
		description = """
				Generic response used when revealing whether an account exists would \
				create a security risk.
				""")
public record AcceptedResponse(
		@Schema(example = "accepted")
		String status) {

	private static final AcceptedResponse ACCEPTED =
			new AcceptedResponse("accepted");

	public static AcceptedResponse accepted() {
		return ACCEPTED;
	}
}
