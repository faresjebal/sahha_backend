package com.sahha.file.attachment;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class AttachmentTokens {
    private final SecureRandom random=new SecureRandom();
    public String issue() { byte[] bytes=new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public String digest(String token) {
        if(token==null || token.length()!=43 || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new com.sahha.file.exception.FileResourceNotFoundException();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
