package com.sahha.communication.attachment;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@SecurityRequirement(name="cookieAuth")
public class AttachmentContextController {
    private final AttachmentAuthority authority;
    public AttachmentContextController(AttachmentAuthority authority) { this.authority=authority; }
    @GetMapping("/api/v1/conversations/{conversationId}/attachment-context")
    public ResponseEntity<AttachmentContext> context(@PathVariable UUID conversationId,
            @RequestParam(required=false) UUID fileId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authority.require(conversationId,
                UUID.fromString(jwt.getClaimAsString("org_id")),UUID.fromString(jwt.getSubject()),jwt.getTokenValue(),fileId));
    }
}
