package com.englishlab.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generación y hash de tokens de un solo uso (recuperación de contraseña y verificación de correo).
 * El token en claro solo viaja por correo; en la BD se guarda únicamente su SHA-256.
 */
public final class TokenUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtils() {
    }

    /** Token aleatorio criptográficamente seguro, en hex (apto para URL sin codificar). */
    public static String newHexToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
