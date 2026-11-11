package com.flux.importacao.interno;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.importacao.interno.leitura.ExtratoLido;

/**
 * Chaves de importação (ADR 0006, decisão 15). Pelo identificador do banco, quando ele existe e
 * não se repete no arquivo: {@code ID:{conta}:{identificador}}, igual no OFX e no CSV. Pela
 * linha sempre: {@code CON:{conta}:{sha256(data|valor|descrição|ordinal)}}, em que o ordinal é a
 * posição da linha entre as iguais do mesmo arquivo (os dois cafés ganham 1 e 2).
 */
final class Chaves {

    private Chaves() {
    }

    /** As duas chaves de uma linha legível; {@code doIdentificador} é nula quando não vale. */
    record DaLinha(String doIdentificador, String doConteudo) {
    }

    /** As chaves de cada linha legível, pelo número da linha. */
    static Map<Integer, DaLinha> doArquivo(long contaId, List<ExtratoLido.Linha> linhas) {
        Map<String, Integer> usosDoIdentificador = new HashMap<>();
        linhas.stream().filter(ExtratoLido.Linha::legivel).filter(linha -> linha.identificador() != null)
                .forEach(linha -> usosDoIdentificador.merge(linha.identificador(), 1, Integer::sum));
        Map<String, Integer> ordinais = new HashMap<>();
        Map<Integer, DaLinha> chaves = new HashMap<>();
        for (ExtratoLido.Linha linha : linhas) {
            if (!linha.legivel()) {
                continue;
            }
            String conteudo = linha.data() + "|" + linha.valor().toPlainString() + "|" + TextoComparavel.chave(linha.descricaoCompleta());
            int ordinal = ordinais.merge(conteudo, 1, Integer::sum);
            String doIdentificador = linha.identificador() != null && usosDoIdentificador.get(linha.identificador()) == 1
                    ? "ID:" + contaId + ":" + linha.identificador() : null;
            chaves.put(linha.numero(), new DaLinha(doIdentificador, "CON:" + contaId + ":" + sha256(conteudo + "|" + ordinal)));
        }
        return chaves;
    }

    /** Todas as chaves possíveis do arquivo, para buscar as que já existem. */
    static List<String> todas(Map<Integer, DaLinha> chaves) {
        List<String> todas = new ArrayList<>();
        chaves.values().forEach(chave -> {
            todas.add(chave.doConteudo());
            if (chave.doIdentificador() != null) {
                todas.add(chave.doIdentificador());
            }
        });
        return todas;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String sha256(String texto) {
        return sha256(texto.getBytes(StandardCharsets.UTF_8));
    }

    /** HMAC-SHA256 do texto com a chave (o hash do extrato, decisão 11, e a derivação da chave dele). */
    static byte[] hmac(byte[] chave, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
