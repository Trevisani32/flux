package com.flux.lancamentos.interno;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fatia 04c (decisão 27): quem pode ser o outro lado de uma transferência convertida. O par tem o
 * mesmo valor com o sinal oposto, fica em outra conta ativa, é receita ou despesa (manual ou
 * importada) que não está excluída nem numa transferência e tem até 5 dias de diferença, para antes
 * ou para depois. Um lançamento de fatura (Fatia 05a) não tem conta e nunca é par. A mais perto da data vem primeiro; no empate, a mais antiga, e depois o menor id.
 */
final class Pares {

    static final int DIAS = 5;

    private Pares() {
    }

    /** Os pares possíveis de {@code fixo} entre {@code outros}, já em ordem. */
    static List<Lancamento> de(Lancamento fixo, Collection<Lancamento> outros, Set<Long> contasAtivas) {
        return outros.stream()
                .filter(outro -> serve(fixo, outro, contasAtivas))
                .sorted(Comparator.comparingLong((Lancamento outro) -> distancia(fixo, outro))
                        .thenComparing(Lancamento::getData)
                        .thenComparing(Lancamento::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** {@code outro} serve de par para {@code fixo}; {@code contasAtivas} são os ids das contas ativas do usuário. */
    static boolean serve(Lancamento fixo, Lancamento outro, Set<Long> contasAtivas) {
        return !Objects.equals(outro.getId(), fixo.getId())
                && !outro.transferencia()
                && outro.getIgnoradoEm() == null
                && outro.getContaId() != null
                && !outro.getContaId().equals(fixo.getContaId())
                && contasAtivas.contains(outro.getContaId())
                && outro.getValor().compareTo(fixo.getValor().negate()) == 0
                && distancia(fixo, outro) <= DIAS;
    }

    private static long distancia(Lancamento fixo, Lancamento outro) {
        return Math.abs(ChronoUnit.DAYS.between(fixo.getData(), outro.getData()));
    }
}
