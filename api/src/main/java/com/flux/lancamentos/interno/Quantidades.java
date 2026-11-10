package com.flux.lancamentos.interno;

import java.util.Locale;

/** "1 lançamento", "1.234 lançamentos", "1 transferência", "3 transferências". */
final class Quantidades {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Quantidades() {
    }

    static String lancamentos(long quantidade) {
        return quantidade == 1 ? "1 lançamento" : String.format(PT_BR, "%,d lançamentos", quantidade);
    }

    static String transferencias(long quantidade) {
        return quantidade == 1 ? "1 transferência" : String.format(PT_BR, "%,d transferências", quantidade);
    }
}
