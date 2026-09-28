package com.sahha.auth.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Authentication invokes the authoritative local decoder before this handler. No record data is exposed. */
@RestController
public class InternalSessionCheckController {
    public static final String CHALLENGE_HEADER = "X-Sahha-Session-Check";

    @GetMapping("/api/v1/internal/auth/session-check")
    public ResponseEntity<Void> check(@RequestHeader(value = CHALLENGE_HEADER, required = false) List<String> challenges) {
        if (challenges == null || challenges.size() != 1 || !validChallenge(challenges.getFirst())) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(CHALLENGE_HEADER, challenges.getFirst()).build();
    }

    private boolean validChallenge(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        }
        catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
