package edu.institution.ims.studentprofile.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AadhaarEncryptionService {
    private static final int IV_LENGTH = 12;
    private final SecretKeySpec key; private final SecureRandom random = new SecureRandom();
    public AadhaarEncryptionService(@Value("${app.profile-encryption-key}") String encodedKey) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedKey); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("PROFILE_ENCRYPTION_KEY must be Base64 encoded", e); }
        if (bytes.length != 32) throw new IllegalArgumentException("PROFILE_ENCRYPTION_KEY must decode to exactly 32 bytes");
        key = new SecretKeySpec(bytes, "AES");
    }
    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) { throw new IllegalStateException("Unable to protect official identity information", e); }
    }
    public String decrypt(String encryptedValue) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedValue);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Unable to read official identity information", e); }
    }
}
