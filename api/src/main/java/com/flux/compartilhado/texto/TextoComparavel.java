package com.flux.compartilhado.texto;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras de texto usadas para comparar nomes digitados: espaços, maiúsculas e acentos
 * não fazem dois nomes diferentes ("Poupança" e "  poupanca " são o mesmo nome).
 */
public final class TextoComparavel {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Pattern ESPACOS = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private TextoComparavel() {
    }

    /** Troca cada sequência de espaços (inclusive o não separável) por um espaço e tira os das pontas. */
    public static String limparEspacos(String texto) {
        return ESPACOS.matcher(texto).replaceAll(" ").strip();
    }

    /** Chave de comparação: sem espaços sobrando, sem acentos e em minúsculas. */
    public static String chave(String texto) {
        String semAcentos = MARCAS.matcher(Normalizer.normalize(limparEspacos(texto), Normalizer.Form.NFD)).replaceAll("");
        return Normalizer.normalize(semAcentos, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /**
     * Ordem alfabética do pt-BR que não diferencia acentos nem maiúsculas. O Collator
     * não é seguro entre threads: cada chamada devolve um comparador novo.
     */
    public static Comparator<String> ordemAlfabetica() {
        Collator collator = Collator.getInstance(PT_BR);
        collator.setStrength(Collator.PRIMARY);
        return collator::compare;
    }
}
