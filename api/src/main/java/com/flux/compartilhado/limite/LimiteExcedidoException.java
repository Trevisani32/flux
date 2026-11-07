package com.flux.compartilhado.limite;

import java.time.Duration;

public class LimiteExcedidoException extends RuntimeException {

    private final Duration espera;

    public LimiteExcedidoException(Duration espera) {
        super("Muitas tentativas. Tente de novo em alguns minutos.");
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }

    /** Valor do cabeçalho Retry-After: segundos arredondados para cima, no mínimo 1. */
    public long segundosParaTentarDeNovo() {
        long segundos = espera.getSeconds() + (espera.getNano() > 0 ? 1 : 0);
        return Math.max(1, segundos);
    }
}
