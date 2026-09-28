package com.sahha.file.attachment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.sahha.file.config.RequestIdFilter;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;

@RestController
@RequestMapping("/api/v1/files/message-attachments")
@SecurityRequirement(name="cookieAuth")
public class MessageAttachmentController {
    private final MessageAttachmentService service;
    public MessageAttachmentController(MessageAttachmentService service) { this.service=service; }
    @PostMapping("/uploads") @SecurityRequirement(name="csrfHeader")
    public ResponseEntity<AttachmentUploadTicket> negotiate(@Valid @RequestBody AttachmentUploadRequest body,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        return own(service.negotiate(org(jwt),user(jwt),jwt.getTokenValue(),body,RequestIdFilter.requestId(request)));
    }
    @PutMapping("/{fileId}/content") @SecurityRequirement(name="csrfHeader") @SecurityRequirement(name="uploadTicket")
    public ResponseEntity<AttachmentResource> upload(@PathVariable UUID fileId,
            @RequestHeader("X-Upload-Token") String token,@RequestHeader(HttpHeaders.CONTENT_TYPE) String type,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request) throws IOException {
        return own(service.upload(fileId,org(jwt),user(jwt),jwt.getTokenValue(),token,type,
                request.getContentLengthLong(),request.getInputStream(),RequestIdFilter.requestId(request)));
    }
    @GetMapping("/{fileId}")
    public ResponseEntity<AttachmentResource> metadata(@PathVariable UUID fileId,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        return own(service.metadata(fileId,org(jwt),user(jwt),jwt.getTokenValue(),RequestIdFilter.requestId(request)));
    }
    @GetMapping("/{fileId}/send-context")
    public ResponseEntity<MessageAttachmentService.ReadyFile> ready(@PathVariable UUID fileId,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        return own(service.ready(fileId,org(jwt),user(jwt),jwt.getTokenValue(),RequestIdFilter.requestId(request)));
    }
    @PostMapping("/{fileId}/download-grants") @SecurityRequirement(name="csrfHeader")
    public ResponseEntity<MedicalFileDownloadGrantResponse> grant(@PathVariable UUID fileId,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        return own(service.grant(fileId,org(jwt),user(jwt),jwt.getTokenValue(),RequestIdFilter.requestId(request)));
    }
    @GetMapping("/{fileId}/content") @SecurityRequirement(name="downloadGrant")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID fileId,@RequestHeader("X-Download-Token") String token,
            @AuthenticationPrincipal Jwt jwt,HttpServletRequest request) {
        var file=service.download(fileId,org(jwt),user(jwt),jwt.getTokenValue(),token,RequestIdFilter.requestId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.originalFilename(),StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType())).contentLength(file.size()).body(new InputStreamResource(file.content()));
    }
    static UUID org(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("org_id")); }
    static UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    static <T> ResponseEntity<T> own(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
