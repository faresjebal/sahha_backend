package com.sahha.clinical.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sahha.clinical.entity.ClinicalVitalSigns;

public interface ClinicalVitalSignsRepository extends JpaRepository<ClinicalVitalSigns, UUID> {
	Optional<ClinicalVitalSigns> findByConsultationId(UUID consultationId);
	@Modifying
	@Query("delete from ClinicalVitalSigns value where value.consultationId = :id")
	int deleteForConsultation(@Param("id") UUID consultationId);
}
