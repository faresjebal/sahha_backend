package com.sahha.organisation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOrganisationProfileRequest(
        @NotBlank @Size(min = 2, max = 160) String name,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @NotBlank @Size(min = 6, max = 32) String phoneNumber,
        @NotBlank @Size(min = 4, max = 300) String address,
        @NotBlank @Size(min = 2, max = 100) String city,
        @NotBlank @Size(min = 2, max = 100) String region,
        @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
        @NotNull @Min(0) Long version) { }
