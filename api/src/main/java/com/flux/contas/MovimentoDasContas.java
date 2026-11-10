package com.flux.contas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Movimento das contas, calculado por quem guarda os lançamentos. O módulo de contas
 * declara a interface e o de lançamentos (Fatia 03b) implementa: assim só lançamentos
 * depende de contas, e não o contrário. Sem implementação (Fatia 03a), o saldo atual de
 * cada conta é o saldo inicial e a data do saldo inicial muda livremente. Pode existir uma
 * implementação só: com duas, a aplicação não sobe ({@code ContaService}).
 */
public interface MovimentoDasContas {

    /**
     * Soma, por conta, dos lançamentos efetivados com data até {@code data}, inclusive
     * (ADR 0003). Conta sem lançamento pode ficar fora do mapa. Os valores têm escala 2.
     */
    Map<Long, BigDecimal> somaPorContaAte(Long usuarioId, LocalDate data);

    /** Data do lançamento mais antigo da conta, se houver. */
    Optional<LocalDate> dataDoPrimeiroLancamento(Long usuarioId, Long contaId);
}
