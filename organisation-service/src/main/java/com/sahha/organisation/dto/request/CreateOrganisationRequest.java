package com.sahha.organisation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sahha.organisation.entity.OrganisationType;

public record CreateOrganisationRequest(
		@NotBlank
		@Size(min = 2, max = 160)
		@Schema(example = "Carthage Family Clinic")
		String name,

		@Size(max = 200)
		@Schema(example = "Carthage Family Clinic SARL")
		String legalName,

		@NotNull
		@Schema(example = "CLINIC")
		OrganisationType type,

		@NotBlank
		@Email
		@Size(max = 254)
		@Schema(example = "contact@carthage-clinic.example")
		String contactEmail,

		@NotBlank
		@Size(min = 6, max = 32)
		@Schema(example = "+216 71 000 000")
		String phoneNumber,

		@NotBlank
		@Size(min = 4, max = 300)
		@Schema(example = "12 Avenue de Carthage")
		String address,

		@NotBlank
		@Size(min = 2, max = 100)
		@Schema(example = "Tunis")
		String city,

		@NotBlank
		@Size(min = 2, max = 100)
		@Schema(example = "Tunis")
		String region,

		@Size(max = 20)
		@Schema(example = "1000")
		String postalCode,

		@NotBlank
		@Pattern(regexp = "^[A-Za-z]{2}$")
		@Schema(example = "TN")
		String countryCode,

		@NotBlank
		@Size(max = 64)
		@Schema(example = "Africa/Tunis")
		String timeZone) {
}
