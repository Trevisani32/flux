package com.flux.importacao.interno;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fatia 05c (decisão 41): a linha da fatura que diz "Parcela k/N" no fim da descrição ("Curso Online Ficticio - Parcela
 * 3/10"), com a palavra "parcela" em qualquer caixa e o separador antes dela opcional. Vale de 2 a 48 parcelas, com k de 1
 * a N, e com texto antes de "Parcela": fora disso, a linha é uma compra à vista com a descrição inteira. A descrição com
 * mais de 300 caracteres também é à vista: nenhuma fatura escreve uma parcela assim, e o limite deixa a leitura linear.
 */
final class ParcelaNaDescricao {

    /** Só o fim: "parcela", o número, a barra e o total. O texto antes é cortado à mão, sem retrocesso. */
    private static final Pattern PARCELA = Pattern.compile("\\bparcela\\s+(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int MAXIMO_DE_PARCELAS = 48;
    private static final int MAXIMO_DA_DESCRICAO = 300;

    private ParcelaNaDescricao() {
    }

    /** {@code descricao}: a do extrato sem "Parcela k/N", cortada em 255; {@code numero} é k e {@code parcelas}, N. */
    record Parcela(String descricao, int numero, int parcelas) {
    }

    static Optional<Parcela> de(String descricaoCompleta) {
        if (descricaoCompleta == null || descricaoCompleta.length() > MAXIMO_DA_DESCRICAO) {
            return Optional.empty();
        }
        Matcher achado = PARCELA.matcher(descricaoCompleta);
        if (!achado.find()) {
            return Optional.empty();
        }
        String antes = semSeparadorNoFim(descricaoCompleta.substring(0, achado.start()));
        int numero = Integer.parseInt(achado.group(1));
        int parcelas = Integer.parseInt(achado.group(2));
        if (antes.isEmpty() || parcelas < 2 || parcelas > MAXIMO_DE_PARCELAS || numero < 1 || numero > parcelas) {
            return Optional.empty();
        }
        String curta = antes.length() > 255 ? antes.substring(0, 255).strip() : antes;
        return Optional.of(new Parcela(curta, numero, parcelas));
    }

    /** O texto antes de "Parcela", sem os espaços, hífens e travessões que o separam dela. */
    private static String semSeparadorNoFim(String texto) {
        int fim = texto.length();
        while (fim > 0) {
            char letra = texto.charAt(fim - 1);
            if (!Character.isWhitespace(letra) && letra != '-' && letra != '–' && letra != '—') {
                break;
            }
            fim--;
        }
        return texto.substring(0, fim).strip();
    }
}
