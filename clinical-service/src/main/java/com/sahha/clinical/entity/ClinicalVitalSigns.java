package com.sahha.clinical.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clinical_vital_signs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalVitalSigns {

	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(name = "measured_at", nullable = false)
	private Instant measuredAt;
	@Column(name = "temperature_celsius", precision = 4, scale = 1)
	private BigDecimal temperatureCelsius;
	@Column(name = "systolic_blood_pressure")
	private Integer systolicBloodPressure;
	@Column(name = "diastolic_blood_pressure")
	private Integer diastolicBloodPressure;
	@Column(name = "heart_rate_bpm")
	private Integer heartRateBpm;
	@Column(name = "respiratory_rate_bpm")
	private Integer respiratoryRateBpm;
	@Column(name = "oxygen_saturation_percent", precision = 5, scale = 2)
	private BigDecimal oxygenSaturationPercent;
	@Column(name = "weight_kg", precision = 6, scale = 2)
	private BigDecimal weightKg;
	@Column(name = "height_cm", precision = 5, scale = 2)
	private BigDecimal heightCm;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "specialty_measurements", nullable = false,
			columnDefinition = "jsonb")
	private Map<String, Object> specialtyMeasurements;

	public static ClinicalVitalSigns create(
			UUID consultationId, Instant measuredAt,
			BigDecimal temperatureCelsius, Integer systolicBloodPressure,
			Integer diastolicBloodPressure, Integer heartRateBpm,
			Integer respiratoryRateBpm, BigDecimal oxygenSaturationPercent,
			BigDecimal weightKg, BigDecimal heightCm,
			Map<String, Object> specialtyMeasurements) {
		ClinicalVitalSigns value = new ClinicalVitalSigns();
		value.id = UUID.randomUUID();
		value.consultationId = Objects.requireNonNull(consultationId);
		value.measuredAt = Objects.requireNonNull(measuredAt);
		value.temperatureCelsius = temperatureCelsius;
		value.systolicBloodPressure = systolicBloodPressure;
		value.diastolicBloodPressure = diastolicBloodPressure;
		value.heartRateBpm = heartRateBpm;
		value.respiratoryRateBpm = respiratoryRateBpm;
		value.oxygenSaturationPercent = oxygenSaturationPercent;
		value.weightKg = weightKg;
		value.heightCm = heightCm;
		value.specialtyMeasurements = Map.copyOf(new LinkedHashMap<>(
				specialtyMeasurements == null ? Map.of() : specialtyMeasurements));
		return value;
	}
}
