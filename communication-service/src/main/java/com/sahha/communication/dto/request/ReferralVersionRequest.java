package com.sahha.communication.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record ReferralVersionRequest(@PositiveOrZero long expectedVersion) {
}
