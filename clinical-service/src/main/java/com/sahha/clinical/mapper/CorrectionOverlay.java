package com.sahha.clinical.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sahha.clinical.entity.ClinicalCorrection;
import com.sahha.clinical.entity.CorrectionTargetType;

final class CorrectionOverlay {

	private final Map<Key, String> effectiveValues = new HashMap<>();

	CorrectionOverlay(List<ClinicalCorrection> corrections) {
		for (ClinicalCorrection correction : corrections) {
			effectiveValues.put(new Key(
					correction.getTargetType(), correction.getTargetId(),
					correction.getFieldName()), correction.getNewValue());
		}
	}

	String text(CorrectionTargetType type, UUID id, String field, String source) {
		Key key = new Key(type, id, field);
		return effectiveValues.containsKey(key) ? effectiveValues.get(key) : source;
	}

	<T extends Enum<T>> T enumValue(
			CorrectionTargetType type, UUID id, String field, T source,
			Class<T> enumType) {
		String value = text(type, id, field, source == null ? null : source.name());
		return value == null ? null : Enum.valueOf(enumType, value);
	}

	Integer integer(
			CorrectionTargetType type, UUID id, String field, Integer source) {
		String value = text(type, id, field, source == null ? null : source.toString());
		return value == null ? null : Integer.valueOf(value);
	}

	BigDecimal decimal(
			CorrectionTargetType type, UUID id, String field, BigDecimal source) {
		String value = text(type, id, field, source == null ? null : source.toPlainString());
		return value == null ? null : new BigDecimal(value);
	}

	Instant instant(
			CorrectionTargetType type, UUID id, String field, Instant source) {
		String value = text(type, id, field, source == null ? null : source.toString());
		return value == null ? null : Instant.parse(value);
	}

	private record Key(CorrectionTargetType type, UUID targetId, String field) {
	}
}
