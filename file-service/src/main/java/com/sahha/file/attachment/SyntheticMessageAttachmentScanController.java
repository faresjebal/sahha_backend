package com.sahha.file.attachment;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.sahha.file.config.RequestIdFilter;
import com.sahha.file.dto.request.SyntheticFileScanDecisionRequest;
import com.sahha.file.service.medicalfilescanservice.FileScanDecision;

@RestController
@ConditionalOnProperty(name="sahha.file.storage.synthetic-clean-enabled",havingValue="true")
@SecurityRequirement(name="cookieAuth")
public class SyntheticMessageAttachmentScanController {
    private final MessageAttachmentService service;
    public SyntheticMessageAttachmentScanController(MessageAttachmentService service) { this.service=service; }
    @PostMapping("/api/v1/files/message-attachments/{fileId}/synthetic-scan")
    @SecurityRequirement(name="csrfHeader")
    public ResponseEntity<AttachmentResource> decide(@PathVariable UUID fileId,
            @Valid @RequestBody SyntheticFileScanDecisionRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        return MessageAttachmentController.own(service.syntheticScan(fileId,MessageAttachmentController.org(jwt),
                MessageAttachmentController.user(jwt),jwt.getTokenValue(),body.decision()==FileScanDecision.CLEAN,RequestIdFilter.requestId(request)));
    }
}
