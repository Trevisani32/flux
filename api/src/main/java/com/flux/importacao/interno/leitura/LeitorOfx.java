package com.flux.importacao.interno.leitura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OFX 1.x (SGML, com ou sem as tags de fechamento dos elementos) e 2.x (XML), pelo mesmo
 * tokenizador tolerante: sem parser de XML, então sem XXE (decisão 39). A data são os 8
 * primeiros dígitos do DTPOSTED, sem converter fuso; o valor aceita ponto ou vírgula decimal
 * (decisão 8); a descrição é o MEMO, ou o NAME (decisão 9).
 */
public final class LeitorOfx implements LeitorExtrato {

    @Override
    public ExtratoLido ler(String texto) {
        int inicio = texto.toUpperCase(Locale.ROOT).indexOf("<OFX>");
        String corpo = inicio < 0 ? texto : texto.substring(inicio);
        List<ExtratoLido.Linha> linhas = new ArrayList<>();
        int contas = 0;
        boolean cartao = false;
        String bancoId = null;
        String contaDoBanco = null;
        Map<String, String> transacao = null;
        int posicao = 0;
        while (true) {
            int abre = corpo.indexOf('<', posicao);
            int fecha = abre < 0 ? -1 : corpo.indexOf('>', abre);
            if (abre < 0 || fecha < 0) {
                break;
            }
            String tag = corpo.substring(abre + 1, fecha).strip();
            int proxima = corpo.indexOf('<', fecha + 1);
            String valor = corpo.substring(fecha + 1, proxima < 0 ? corpo.length() : proxima).strip();
            posicao = fecha + 1;
            if (tag.startsWith("?") || tag.startsWith("!")) {
                continue;
            }
            boolean fechamento = tag.startsWith("/");
            String nome = (fechamento ? tag.substring(1) : tag).toUpperCase(Locale.ROOT);
            if (nome.equals("STMTTRN") || (fechamento && nome.equals("BANKTRANLIST"))) {
                if (transacao != null) {
                    linhas.add(linha(linhas.size() + 1, transacao));
                }
                transacao = nome.equals("STMTTRN") && !fechamento ? new HashMap<>() : null;
                continue;
            }
            if (fechamento) {
                continue;
            }
            switch (nome) {
                case "STMTRS" -> contas++;
                case "CCSTMTRS", "CREDITCARDMSGSRSV1" -> cartao = true;
                case "BANKID" -> bancoId = bancoId == null ? valor : bancoId;
                case "ACCTID" -> contaDoBanco = contaDoBanco == null ? valor : contaDoBanco;
                default -> {
                    if (transacao != null && !valor.isEmpty()) {
                        transacao.put(nome, entidades(valor));
                    }
                }
            }
        }
        if (transacao != null) {
            linhas.add(linha(linhas.size() + 1, transacao));
        }
        return new ExtratoLido(ExtratoLido.Formato.OFX, linhas, bancoId, contaDoBanco, contas, cartao);
    }

    private static ExtratoLido.Linha linha(int numero, Map<String, String> transacao) {
        LocalDate data;
        try {
            String quando = transacao.getOrDefault("DTPOSTED", "");
            data = LocalDate.parse(quando.substring(0, Math.min(8, quando.length())), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException ex) {
            return ExtratoLido.Linha.ilegivel(numero, "data ilegível");
        }
        BigDecimal valor = valor(transacao.getOrDefault("TRNAMT", ""));
        if (valor == null) {
            return ExtratoLido.Linha.ilegivel(numero, "valor ilegível");
        }
        String memo = transacao.get("MEMO");
        String descricao = memo != null && !memo.isBlank() ? memo : transacao.get("NAME");
        return ExtratoLido.Linha.legivel(numero, data, valor, descricao, transacao.get("FITID"));
    }

    /**
     * "-50.00", "-50,00", "-1,234.56" e "-1.234,56": o último separador é o decimal. Mais de 2 casas
     * ou acima de {@link ExtratoLido#VALOR_MAXIMO}: nulo.
     */
    static BigDecimal valor(String texto) {
        String limpo = texto.replace(" ", "").replace("+", "");
        if (limpo.lastIndexOf(',') > limpo.lastIndexOf('.')) {
            limpo = limpo.replace(".", "").replace(',', '.');
        } else {
            limpo = limpo.replace(",", "");
        }
        try {
            BigDecimal valor = new BigDecimal(limpo);
            return ExtratoLido.valorAceito(valor) ? valor : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String entidades(String valor) {
        return valor.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&nbsp;", " ").replace("&amp;", "&");
    }
}
