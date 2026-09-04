package com.sahha.clinical.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sahha.clinical.entity.ClinicalCorrection;

public interface ClinicalCorrectionRepository extends JpaRepository<ClinicalCorrection, UUID> {
	List<ClinicalCorrection> findByConsultationIdOrderByConsultationVersionAscCorrectedAtAscIdAsc(
			UUID consultationId);
	long countByConsultationId(UUID consultationId);
}
