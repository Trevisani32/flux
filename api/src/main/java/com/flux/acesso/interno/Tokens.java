package com.flux.acesso.interno;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Valores aleatórios de 256 bits (refresh token e links por e-mail) e o hash guardado no banco. */
public final class Tokens {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Tokens() {
    }

    /** 32 bytes aleatórios em Base64 URL sem padding (43 caracteres). */
    public static String novoValor() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal minúsculo (64 caracteres). */
    public static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}
