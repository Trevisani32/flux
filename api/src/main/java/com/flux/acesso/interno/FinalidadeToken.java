package com.flux.acesso.interno;

import java.time.Duration;

/** Finalidade de um link enviado por e-mail, com a validade aprovada no ADR 0009. */
public enum FinalidadeToken {

    CONFIRMACAO(Duration.ofHours(24)),
    REDEFINICAO(Duration.ofMinutes(30)),
    TROCA_DE_EMAIL(Duration.ofHours(24));

    private final Duration validade;

    FinalidadeToken(Duration validade) {
        this.validade = validade;
    }

    public Duration validade() {
        return validade;
    }
}
