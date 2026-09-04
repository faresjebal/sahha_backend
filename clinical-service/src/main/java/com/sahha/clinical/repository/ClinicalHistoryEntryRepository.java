package com.sahha.clinical.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.sahha.clinical.entity.ClinicalHistoryEntry;

public interface ClinicalHistoryEntryRepository extends JpaRepository<ClinicalHistoryEntry, UUID> {
	List<ClinicalHistoryEntry> findByConsultationIdOrderByPosition(UUID consultationId);
	@Modifying
	@Query("delete from ClinicalHistoryEntry value where value.consultationId = :id")
	int deleteForConsultation(@Param("id") UUID consultationId);
}
