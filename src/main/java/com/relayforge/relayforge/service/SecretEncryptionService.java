package com.relayforge.relayforge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretEncryptionService {
    private final byte[] key;
    public SecretEncryptionService(@Value("${relayforge.encryption.key:}") String configuredKey) {
        key = configuredKey.isBlank() ? null : Base64.getDecoder().decode(configuredKey);
        if (key != null && key.length != 32) throw new IllegalArgumentException("Encryption key must be base64-encoded 32 bytes");
    }
    public String encrypt(String secret) {
        if (key == null) throw new IllegalStateException("Signing requires RELAYFORGE_ENCRYPTION_KEY");
        try { byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce)); byte[] cipher=c.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)); byte[] out=new byte[nonce.length+cipher.length]; System.arraycopy(nonce,0,out,0,nonce.length); System.arraycopy(cipher,0,out,nonce.length,cipher.length); return Base64.getEncoder().encodeToString(out); }
        catch(Exception ex){throw new IllegalStateException("Unable to encrypt signing secret");}
    }
    public String decrypt(String encrypted) {
        if (key == null) throw new IllegalStateException("Signing requires RELAYFORGE_ENCRYPTION_KEY");
        try { byte[] all=Base64.getDecoder().decode(encrypted), nonce=java.util.Arrays.copyOfRange(all,0,12), data=java.util.Arrays.copyOfRange(all,12,all.length); Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce)); return new String(c.doFinal(data),java.nio.charset.StandardCharsets.UTF_8); }
        catch(Exception ex){throw new IllegalStateException("Unable to decrypt signing secret");}
    }
}
