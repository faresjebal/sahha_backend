package com.sahha.communication.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sahha.communication.entity.CommunicationAuditEvent;

public interface CommunicationAuditEventRepository extends JpaRepository<CommunicationAuditEvent, UUID> { }
