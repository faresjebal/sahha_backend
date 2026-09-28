package com.sahha.auth.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Account identity only. Email/credentials and patient records have separate workflows. */
public record UpdateAccountProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phoneNumber,
        @NotNull @Min(0) Long version) { }
