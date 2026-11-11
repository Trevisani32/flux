package com.flux.importacao.interno.leitura;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.flux.compartilhado.texto.TextoComparavel;

/**
 * Bytes do arquivo → texto (decisão 7). O BOM decide (UTF-8, UTF-16LE ou UTF-16BE); sem BOM, o
 * texto é lido como UTF-8 estrito e, se não for UTF-8 válido, como windows-1252, que cobre o
 * ISO-8859-1 nas letras do português. O CHARSET do cabeçalho do OFX é ignorado de propósito.
 */
public final class TextoDoArquivo {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private TextoDoArquivo() {
    }

    public static String decodificar(byte[] bytes) {
        if (comeca(bytes, 0xEF, 0xBB, 0xBF)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (comeca(bytes, 0xFF, 0xFE)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (comeca(bytes, 0xFE, 0xFF)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException naoEhUtf8) {
            return new String(bytes, WINDOWS_1252);
        }
    }

    /** PDF, ZIP (planilha do Excel) ou byte nulo no começo, sem BOM de UTF-16: não é texto. */
    public static boolean binario(byte[] bytes) {
        if (comeca(bytes, '%', 'P', 'D', 'F') || comeca(bytes, 'P', 'K', 3, 4)) {
            return true;
        }
        if (comeca(bytes, 0xFF, 0xFE) || comeca(bytes, 0xFE, 0xFF)) {
            return false;
        }
        for (int i = 0; i < Math.min(bytes.length, 1024); i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** OFX 1.x tem o cabeçalho OFXHEADER; o 2.x, a raiz &lt;OFX&gt;. */
    public static boolean ofx(String texto) {
        String maiusculo = texto.toUpperCase(Locale.ROOT);
        return maiusculo.contains("<OFX>") || maiusculo.stripLeading().startsWith("OFXHEADER");
    }

    /** O cabeçalho do CSV da conta do Nubank (decisão 6). */
    public static boolean cabecalhoDoNubank(String texto) {
        return TextoComparavel.chave(primeiraLinha(texto)).equals("data,valor,identificador,descricao");
    }

    /** O cabeçalho do CSV da fatura do cartão Nubank, que é da Fatia 05 (decisão 10). */
    public static boolean faturaDoNubank(String texto) {
        return TextoComparavel.chave(primeiraLinha(texto)).equals("date,title,amount");
    }

    private static String primeiraLinha(String texto) {
        return texto.lines().filter(linha -> !linha.isBlank()).findFirst().orElse("");
    }

    private static boolean comeca(byte[] bytes, int... prefixo) {
        if (bytes.length < prefixo.length) {
            return false;
        }
        for (int i = 0; i < prefixo.length; i++) {
            if ((bytes[i] & 0xFF) != prefixo[i]) {
                return false;
            }
        }
        return true;
    }
}
