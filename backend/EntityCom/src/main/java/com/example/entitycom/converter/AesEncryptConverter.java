package com.example.entitycom.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/*
 * DB 컬럼 암호화 (채팅 내용, 닉네임, 연동 토큰)
 *
 * v2 (신규 저장): AES-256-GCM, 값마다 무작위 IV(12바이트) + 위변조 검증 태그
 *   저장 형식: "v2:" + base64(IV || 암호문+태그)
 *   키: 환경변수 CHAT_ENCRYPT_KEY_V2 = base64로 인코딩한 32바이트 (예: openssl rand -base64 32)
 *
 * legacy (기존 데이터 읽기용): AES-CBC + 고정 IV, 키가 코드 기본값이라 사실상 공개된 상태.
 *   기존 행을 읽기 위해서만 남겨 둠. 행이 다시 저장되면 v2로 바뀜.
 *
 * 주의: v2는 같은 평문도 매번 다른 암호문이 되므로 암호화 컬럼으로 = 조회(findByXxx)는 불가.
 */
@Slf4j
@Converter
public class AesEncryptConverter implements AttributeConverter<String, String> {

    private static final String V2_PREFIX = "v2:";
    private static final String V2_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final String LEGACY_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final byte[] LEGACY_KEY = toBytes(System.getenv().getOrDefault("CHAT_ENCRYPT_KEY", "JoGptDefaultKey1234567890123456"), 32);
    private static final byte[] LEGACY_IV = toBytes(System.getenv().getOrDefault("CHAT_ENCRYPT_IV", "JoGptDefaultIV12"), 16);

    private static final byte[] V2_KEY = loadV2Key();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] loadV2Key() {
        String encoded = System.getenv("CHAT_ENCRYPT_KEY_V2");
        if (encoded == null || encoded.isBlank()) {
            log.warn("[AesEncryptConverter] CHAT_ENCRYPT_KEY_V2 미설정 → 예전(고정 IV·기본키) 방식으로 저장합니다. 운영에서는 반드시 설정하세요.");
            return null;
        }
        byte[] key = Base64.getDecoder().decode(encoded.trim());
        if (key.length != 32) {
            throw new IllegalStateException("CHAT_ENCRYPT_KEY_V2는 base64로 인코딩한 32바이트여야 합니다. 현재 " + key.length + "바이트");
        }
        return key;
    }

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
            if (V2_KEY == null) return encryptLegacy(plainText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(V2_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(V2_KEY, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return V2_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) return null;
        if (cipherText.startsWith(V2_PREFIX)) {
            if (V2_KEY == null) {
                throw new IllegalStateException("v2 암호문인데 CHAT_ENCRYPT_KEY_V2가 설정되지 않았습니다.");
            }
            try {
                byte[] data = Base64.getDecoder().decode(cipherText.substring(V2_PREFIX.length()));
                Cipher cipher = Cipher.getInstance(V2_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(V2_KEY, "AES"),
                        new GCMParameterSpec(GCM_TAG_BITS, data, 0, GCM_IV_LENGTH));
                return new String(cipher.doFinal(data, GCM_IV_LENGTH, data.length - GCM_IV_LENGTH), StandardCharsets.UTF_8);
            } catch (Exception e) {
                // 위변조·키 불일치 → 평문인 척 돌려주지 않고 실패시킴
                throw new IllegalStateException("복호화 실패(v2)", e);
            }
        }
        try {
            Cipher cipher = Cipher.getInstance(LEGACY_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(LEGACY_KEY, "AES"), new IvParameterSpec(LEGACY_IV));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 기존 평문 데이터(암호화 전)는 그대로 반환
            return cipherText;
        }
    }

    private static String encryptLegacy(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(LEGACY_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(LEGACY_KEY, "AES"), new IvParameterSpec(LEGACY_IV));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }
}
