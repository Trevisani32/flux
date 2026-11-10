package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.flux.contas.MovimentoDasContas;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** O saldo das contas a partir dos lançamentos (ADR 0003), para o módulo de contas. */
@Component
public class MovimentoPorLancamentos implements MovimentoDasContas {

    private final LancamentoRepository repositorio;

    public MovimentoPorLancamentos(LancamentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Todos os tipos entram, inclusive as transferências (ADR 0004). */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> somaPorContaAte(Long usuarioId, LocalDate data) {
        return repositorio.somarPorContaAte(usuarioId, data).stream()
                .collect(Collectors.toMap(SomaDaConta::contaId, soma -> soma.soma().setScale(2)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> dataDoPrimeiroLancamento(Long usuarioId, Long contaId) {
        return repositorio.primeiraDataDaConta(usuarioId, contaId);
    }
}
