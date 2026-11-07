package com.flux.compartilhado.limite;

import java.time.Duration;

/**
 * Limite de tentativas por chave, com janela deslizante. A implementação atual
 * guarda os contadores em memória; outra (Postgres, Redis) pode entrar no lugar.
 */
public interface LimiteDeTentativas {

    /**
     * Conta uma tentativa para a chave. Se a chave já tem {@code maximo} tentativas
     * dentro da janela, não conta e lança {@link LimiteExcedidoException}.
     */
    void consumir(String chave, int maximo, Duration janela);

    /** Desconta a tentativa mais recente da chave. Não faz nada se não houver. */
    void devolver(String chave);
}
