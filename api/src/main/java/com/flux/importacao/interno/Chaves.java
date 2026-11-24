package com.flux.importacao.interno;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
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
 * posição da linha entre as iguais do mesmo arquivo (os dois cafés ganham 1 e 2). Desde a Fatia 05c (decisão 43), na
 * fatura de cartão, as mesmas com o cartão no lugar da conta, {@code CID:} e {@code CCON:}, e, na linha "Parcela k/N", a
 * chave da compra mais o número: {@code CPAR:{cartão}:{sha256(data|descrição sem "Parcela k/N"|N|ordinal)}:{k}}, que as
 * parcelas seguintes gravam com o número delas. A chave da compra não leva o valor (escolha do dono, ADR 0019): o
 * centavo da divisão muda de uma parcela para outra, e a mesma compra precisa da mesma chave em todo mês.
 */
final class Chaves {

    private Chaves() {
    }

    /** As duas chaves de uma linha legível; {@code doIdentificador} é nula quando não vale. */
    record DaLinha(String doIdentificador, String doConteudo) {
    }

    /**
     * Fatia 05c: as chaves de uma linha legível da fatura. Na linha "Parcela k/N", {@code base} é a chave da compra (sem o
     * ":k"), {@code doConteudo} é "{base}:{k}", {@code doIdentificador} é nula, e {@code semente} e {@code ordinal} fazem a
     * base ({@link #baseDaParcela}): o classificador procura a base da compra a partir desta, entre os ordinais da mesma
     * semente. Nas outras linhas, {@code base} e {@code semente} são nulas e {@code ordinal} é zero.
     */
    record DaFatura(String doIdentificador, String doConteudo, String base, String semente, int ordinal) {
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

    /**
     * Fatia 05c: as chaves de cada linha legível da fatura, pelo número da linha. {@code parcelas}: as linhas "Parcela k/N"
     * da fatura, pelo número. O ordinal da parcela é a posição entre as linhas da mesma loja, data, N e k do arquivo, da
     * menor para a maior pelo valor (e, no empate, pela linha): duas compras parceladas na mesma loja no mesmo dia ganham
     * bases diferentes, na mesma ordem em todo mês, e as parcelas de cada uma as reencontram nas faturas seguintes.
     */
    static Map<Integer, DaFatura> daFatura(long cartaoId, List<ExtratoLido.Linha> linhas,
                                           Map<Integer, ParcelaNaDescricao.Parcela> parcelas) {
        Map<String, Integer> usosDoIdentificador = new HashMap<>();
        linhas.stream().filter(ExtratoLido.Linha::legivel).filter(linha -> linha.identificador() != null)
                .forEach(linha -> usosDoIdentificador.merge(linha.identificador(), 1, Integer::sum));
        Map<String, List<ExtratoLido.Linha>> parcelasIguais = new HashMap<>();
        for (ExtratoLido.Linha linha : linhas) {
            ParcelaNaDescricao.Parcela parcela = parcelas.get(linha.numero());
            if (linha.legivel() && parcela != null) {
                parcelasIguais.computeIfAbsent(semente(linha, parcela) + "|" + parcela.numero(), chave -> new ArrayList<>()).add(linha);
            }
        }
        Map<Integer, Integer> ordinalDaParcela = new HashMap<>();
        parcelasIguais.values().forEach(iguais -> {
            iguais.sort(Comparator.comparing(ExtratoLido.Linha::valor).thenComparingInt(ExtratoLido.Linha::numero));
            for (int posicao = 0; posicao < iguais.size(); posicao++) {
                ordinalDaParcela.put(iguais.get(posicao).numero(), posicao + 1);
            }
        });
        Map<String, Integer> ordinais = new HashMap<>();
        Map<Integer, DaFatura> chaves = new HashMap<>();
        for (ExtratoLido.Linha linha : linhas) {
            if (!linha.legivel()) {
                continue;
            }
            ParcelaNaDescricao.Parcela parcela = parcelas.get(linha.numero());
            if (parcela != null) {
                String semente = semente(linha, parcela);
                int ordinal = ordinalDaParcela.get(linha.numero());
                String base = baseDaParcela(cartaoId, semente, ordinal);
                chaves.put(linha.numero(), new DaFatura(null, base + ":" + parcela.numero(), base, semente, ordinal));
                continue;
            }
            String conteudo = linha.data() + "|" + linha.valor().toPlainString() + "|" + TextoComparavel.chave(linha.descricaoCompleta());
            int ordinal = ordinais.merge(conteudo, 1, Integer::sum);
            String doIdentificador = linha.identificador() != null && usosDoIdentificador.get(linha.identificador()) == 1
                    ? "CID:" + cartaoId + ":" + linha.identificador() : null;
            chaves.put(linha.numero(), new DaFatura(doIdentificador, "CCON:" + cartaoId + ":" + sha256(conteudo + "|" + ordinal), null,
                    null, 0));
        }
        return chaves;
    }

    /** Fatia 05c: a base da compra parcelada, {@code CPAR:{cartão}:{sha256(semente|ordinal)}}. */
    static String baseDaParcela(long cartaoId, String semente, int ordinal) {
        return "CPAR:" + cartaoId + ":" + sha256(semente + "|" + ordinal);
    }

    /** A compra parcelada sem o valor: a data, a loja (a descrição sem "Parcela k/N", como o Flux compara textos) e N. */
    private static String semente(ExtratoLido.Linha linha, ParcelaNaDescricao.Parcela parcela) {
        return linha.data() + "|" + TextoComparavel.chave(parcela.descricao()) + "|" + parcela.parcelas();
    }

    /** Fatia 05c: todas as chaves possíveis da fatura, para buscar as que já existem. */
    static List<String> todasDaFatura(Map<Integer, DaFatura> chaves) {
        List<String> todas = new ArrayList<>();
        chaves.values().forEach(chave -> {
            todas.add(chave.doConteudo());
            if (chave.doIdentificador() != null) {
                todas.add(chave.doIdentificador());
            }
        });
        return todas;
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
