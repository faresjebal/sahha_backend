package com.sahha.clinical.client.communication;

import java.time.Instant;
import java.util.UUID;

public record ShareAccessDecisionResource(
        Boolean allowed, UUID grantId, UUID referralId, Instant validUntil) { }
