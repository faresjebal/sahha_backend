package com.sahha.auth.service.emailservice;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class RenderedAuthEmail {

	@ToString.Include
	private final String subject;

	@Getter(onMethod_ = @JsonIgnore)
	private final String plainText;

	@Getter(onMethod_ = @JsonIgnore)
	private final String html;

	public RenderedAuthEmail(String subject, String plainText, String html) {
		this.subject = requireText(subject, "subject");
		this.plainText = requireText(plainText, "plainText");
		this.html = requireText(html, "html");
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return Objects.requireNonNull(value).strip();
	}
}
