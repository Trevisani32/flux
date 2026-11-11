package com.flux.categorizacao.interno;

import java.util.regex.Pattern;

import com.flux.compartilhado.texto.TextoComparavel;

/**
 * A comparação das regras (decisão 4): a dos nomes e da busca ({@link TextoComparavel#chave(String)}:
 * sem maiúsculas, acentos e espaços repetidos) e, antes dela, pontuação e símbolos viram espaço. Assim
 * "UBER *TRIP" e "UBER* TRIP" dão "uber trip", e "pag uber" casa com "PAG*UBERTRIP 8812".
 */
public final class Comparacao {

    private static final Pattern PONTUACAO = Pattern.compile("[\\p{P}\\p{S}]+");

    private Comparacao() {
    }

    public static String chave(String texto) {
        return TextoComparavel.chave(PONTUACAO.matcher(texto).replaceAll(" "));
    }

    /** O texto de uma regra precisa de ao menos 2 letras ou números depois da comparação (decisão 5). */
    public static boolean temLetrasOuNumerosSuficientes(String chave) {
        return chave.codePoints().filter(Character::isLetterOrDigit).limit(2).count() == 2;
    }
}
