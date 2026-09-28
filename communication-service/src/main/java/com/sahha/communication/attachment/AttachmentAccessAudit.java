package com.sahha.communication.attachment;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.sahha.communication.entity.CommunicationAuditEvent;
import com.sahha.communication.repository.CommunicationAuditEventRepository;
@Service
public class AttachmentAccessAudit {
    private final CommunicationAuditEventRepository repository;
    private final Clock clock;
    public AttachmentAccessAudit(CommunicationAuditEventRepository repository, Clock clock) {
        this.repository=repository; this.clock=clock;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(UUID org, UUID conversation, UUID actor, boolean allowed) {
        repository.save(CommunicationAuditEvent.record(org, conversation, null, actor,
                allowed ? "ATTACHMENT_CONTEXT_ALLOWED" : "ATTACHMENT_CONTEXT_DENIED", 0, clock.instant()));
    }
}
