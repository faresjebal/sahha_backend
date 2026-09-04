package com.sahha.clinical.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sahha.clinical.entity.ClinicalMedicationItem;

public interface ClinicalMedicationItemRepository extends JpaRepository<ClinicalMedicationItem, UUID> {
	List<ClinicalMedicationItem> findByConsultationIdOrderByPosition(UUID consultationId);
	@Modifying
	@Query("delete from ClinicalMedicationItem value where value.consultationId = :id")
	int deleteForConsultation(@Param("id") UUID consultationId);
}
