package com.flux.categorizacao.interno;

/** Como a regra compara o texto dela com a descrição (decisão 5), as duas já na chave da {@link Comparacao}. */
public enum Condicao {

    CONTEM("contém"),
    COMECA_COM("começa com"),
    IGUAL_A("igual a");

    private final String rotulo;

    Condicao(String rotulo) {
        this.rotulo = rotulo;
    }

    /** "contém", "começa com" ou "igual a", para as mensagens. */
    public String rotulo() {
        return rotulo;
    }

    /** {@code alvo} e {@code texto} já passaram pela {@link Comparacao#chave(String)}. */
    public boolean casa(String alvo, String texto) {
        return switch (this) {
            case CONTEM -> alvo.contains(texto);
            case COMECA_COM -> alvo.startsWith(texto);
            case IGUAL_A -> alvo.equals(texto);
        };
    }
}
