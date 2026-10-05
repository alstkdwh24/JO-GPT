package com.example.entitycom.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Converter
public class AesEncryptConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    // 환경변수 CHAT_ENCRYPT_KEY: 32자 문자열 (AES-256)
    // 환경변수 CHAT_ENCRYPT_IV: 16자 문자열
    private static final byte[] KEY = toBytes(System.getenv().getOrDefault("CHAT_ENCRYPT_KEY", "JoGptDefaultKey1234567890123456"), 32);
    private static final byte[] IV  = toBytes(System.getenv().getOrDefault("CHAT_ENCRYPT_IV",  "JoGptDefaultIV12"), 16);

    private static byte[] toBytes(String value, int length) {
        byte[] result = new byte[length];
        byte[] src = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, result, 0, Math.min(src.length, length));
        return result;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(KEY, "AES"),
                    new IvParameterSpec(IV));
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(KEY, "AES"),
                    new IvParameterSpec(IV));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 기존 평문 데이터(암호화 전)는 그대로 반환
            return cipherText;
        }
    }
}
