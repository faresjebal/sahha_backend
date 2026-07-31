package com.sahha.auth.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.auth.security.RsaKeyMaterial;

@RestController
@Hidden
public class AuthKeyController {

	private final RsaKeyMaterial keyMaterial;

	public AuthKeyController(RsaKeyMaterial keyMaterial) {
		this.keyMaterial = keyMaterial;
	}

	@GetMapping("/.well-known/jwks.json")
	public ResponseEntity<Map<String, Object>> jwks() {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(
						java.time.Duration.ofMinutes(5)))
				.body(keyMaterial.publicJwkSet());
	}
}
