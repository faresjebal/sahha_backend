package com.sahha.clinical.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sahha.clinical.entity.ClinicalExaminationFinding;

public interface ClinicalExaminationFindingRepository extends JpaRepository<ClinicalExaminationFinding, UUID> {
	List<ClinicalExaminationFinding> findByConsultationIdOrderByPosition(UUID consultationId);
	@Modifying
	@Query("delete from ClinicalExaminationFinding value where value.consultationId = :id")
	int deleteForConsultation(@Param("id") UUID consultationId);
}
