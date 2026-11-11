package com.flux.importacao.interno.leitura;

/**
 * Lê um extrato já decodificado (ADR 0006). Hoje há o {@link LeitorOfx} e o {@link LeitorCsv};
 * uma fonte futura, como um agregador de Open Finance, é só mais uma implementação.
 */
public interface LeitorExtrato {

    ExtratoLido ler(String texto);
}
