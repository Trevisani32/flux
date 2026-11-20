package com.flux.cartoes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * O que os lançamentos sabem das faturas de cada cartão, para a tela de Cartões e o Início (decisões 26 e
 * 29). O módulo de cartões declara e o de lançamentos implementa, como o {@code MovimentoDasContas}.
 * Sem implementação, a fatura aberta vem das datas padrão, zerada.
 */
public interface MovimentoDosCartoes {

    /** Por id de cartão: a fatura aberta em {@code hoje}, a seguinte e o total das abertas e futuras. */
    Map<Long, Movimento> movimento(Long usuarioId, List<CartaoResumo> cartoes, LocalDate hoje);

    /** Uma fatura, gravada ou não. {@code total} = compras − estornos (negativo é crédito), escala 2. */
    record FaturaResumida(YearMonth mes, LocalDate inicio, LocalDate fechamento, LocalDate vencimento, BigDecimal total, long lancamentos) {
    }

    /**
     * {@code emAberto} soma os totais da aberta e das futuras, e {@code lancamentosEmAberto} conta os
     * lançamentos delas (decisão 5: a confirmação de arquivar diz quanto).
     */
    record Movimento(FaturaResumida aberta, FaturaResumida proxima, BigDecimal emAberto, long lancamentosEmAberto) {
    }
}
